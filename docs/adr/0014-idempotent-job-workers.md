# 0014 — Idempotent job workers through a processed-job record

- **Status:** Accepted
- **Date:** 2026-10-18

## Context

Zeebe hands out jobs **at least once**. A worker's database transaction commits before the worker
completes the job; if that completion fails or the job times out, Zeebe activates the job again and the
work runs a second time. For work that only touches our own database that is harmless, but most workers
here have an external side effect: ordering the bike, issuing the contract, binding and revoking the
policy, asking the dealer to cancel, booking costs, and sending customer mails. Repeating them orders a
second bike or sends a second contract.

[distributed-horcruxes](https://github.com/emaarco/distributed-horcruxes) records processed operations in
a table and wraps each service in a check → execute → record step keyed by `businessId-elementId`. That
cannot be copied unchanged:

- The architecture rules (ADR-0002) do not allow a shared executor in `application.service`: it would
  need the `Service` suffix, implement exactly one inbound port, and be called by other services.
- `businessId-elementId` is not unique per execution. `serviceTask_orderBike` runs again after a customer
  accepts an alternative bike, so a recorded first attempt would block the second order.
- Check-then-record lets two concurrent deliveries of one job (a job timeout while the first is still
  running) both pass the check.
- A skipped repeat still has to answer the engine: the order id, `cancellationPossible`, or the BPMN error
  `bikeUnavailable` from the first execution.

## Decision

We make job workers idempotent **per Zeebe job key** with a generic `IdempotentJobExecutor` in
`common-zeebe`. Workers wrap their use-case call in `runOnce(job) { … }`, or in
`runOnceWithVariables(job) { … }` when the job returns variables. The application layer is unchanged.

In one transaction the executor:

1. looks up the job key. If it is recorded, it answers with the stored outcome: the same variables, or
   the same BPMN error thrown again.
2. otherwise **claims** the key by inserting it first, so a concurrent delivery blocks on the primary key.
3. runs the work and records its outcome: the completion variables as JSON, or the error code and message
   of a `BpmnError`.

Any other exception rolls the transaction back. The record disappears with it, and the job's retry runs
the work again. The use cases join this transaction, so the outcome commits together with the read-model
changes.

`common-zeebe` defines the `ProcessedJobStore` interface. The app implements it in
`ProcessedJobPersistenceAdapter` on the `processed_job` table (Flyway `V2`). Every worker with an external
effect uses the executor. `activateLeasing` does not: it only sets a status, so running it twice is
harmless.

## Consequences

- **Positive:** A repeated delivery repeats neither the side effect nor the read-model write. It answers
  the engine exactly as the first execution did. One mechanism covers every worker, including those whose
  result has no home in the domain (`cancellationPossible`).
- **Negative / trade-offs:** Every job costs one row and three extra statements (lookup, claim, record). `processed_job` grows
  without bound, and cleanup is not part of this decision. A job key is unique only within one Zeebe
  cluster: resetting the cluster but keeping the database makes old keys reappear, so `processed_job` must
  be truncated with it. The process tests clear the table before each test for exactly this reason.
- **Not covered:** If the process crashes after the external call but before the commit, the work runs
  again. Only the external system can close that gap, through an idempotency key it understands.
  [#26](https://github.com/miragon-blueprints/zeebe-example/issues/26) covers the other direction, from our
  transaction to the engine.

## Implementation notes

- `OrderBikeWorker` turns `BikeUnavailableException` into `CamundaError.bpmnError` inside the work, so the
  executor records it. `OrderBikeService` keeps `noRollbackFor`, so the chosen alternative bike still
  commits with the recorded error.
- The executor serialises variables with the Camunda client's `JsonMapper` bean, the same mapper the
  client uses to send them.

# 0015 — Transactional outbox for process messages

- **Status:** Accepted
- **Date:** 2026-10-18

## Context

The application services write the read model and publish a Zeebe message in one `@Transactional`
method (submit, contract signed, handover reported, withdrawn). The publish goes out before the commit:

- Zeebe can hand the follow-up job to a worker before the transaction commits, and the worker reads the
  state from before the change ([#26](https://github.com/miragon-blueprints/zeebe-example/issues/26)).
- A rollback after the publish leaves the engine with a message for a change that never happened.
- If Zeebe is unreachable, the whole request fails, although the database could have taken the change.

[distributed-horcruxes](https://github.com/emaarco/distributed-horcruxes) solves this with an outbox
(`examples/outbox-pattern`): a `PENDING`/`SENT` status, a retry counter and a scheduler that publishes
the pending rows. We adopt the idea and leave out what a blueprint does not need.

## Decision

We publish process messages through a **transactional outbox**. `LeasingProcessAdapter` writes each
message to the table `process_message_outbox` (Flyway `V3`) via `ProcessMessageOutboxPersistenceAdapter`.
The row commits or rolls back with the use case's read-model changes. The port `LeasingProcess` and
the services are unchanged.

`ProcessMessageRelayAdapter` runs every 200 ms. For each message, in one transaction of its own, it:

1. locks the oldest row with `SELECT … FOR UPDATE SKIP LOCKED`,
2. publishes it to Zeebe with the row id as `messageId`,
3. deletes the row.

If publishing fails, the transaction rolls back and the row stays. The run stops with a warning, and the
next run tries again.

Compared with distributed-horcruxes:

- **Delete instead of a status.** The table holds only what still has to go out. There is no `SENT`
  history to clean up and no retry counter.
- **The row id as `messageId`.** Zeebe drops a second publish with the same id within the 10 s TTL.
  That covers a crash between publish and delete. Unlike `correlationKey-messageName`, it does not
  block a legitimate second message of the same name.

## Consequences

- **Positive:** A message reaches the engine only after its transaction has committed, so workers see
  the committed state. Rolled-back requests send nothing. Requests succeed while Zeebe is down, and the
  messages follow once it is back.
- **Negative / trade-offs:** Delivery is at least once and lags by up to one polling interval. A message
  that cannot be published blocks the ones behind it until it goes through. Order is only guaranteed
  with a single relay instance; several instances skip each other's locked rows and can overtake.
  Every message costs an insert, a locked select and a delete.
- **Not covered:** Completing the `Clarify alternative with customer` user task is a command, not a
  message, and still runs inside the transaction. #26 stays open for it.

## Implementation notes

- `@EnableScheduling` sits on `BikeLeasingApplication`. A top-level `config` package would break the
  containment rule (ADR-0002).
- The variables are serialised with the Camunda client's `JsonMapper` bean and published as the stored
  JSON string, so the relay never parses them.
- `jakarta.persistence.lock.timeout = -2` is Hibernate's value for `SKIP LOCKED`. Postgres and the H2
  database used in tests both support it.

# Kotlin + Gradle variant

The bike-leasing service in **Kotlin 2.4**, built with **Gradle** and a `libs.versions.toml` version
catalog, on Spring Boot 4 and Camunda 8.9 (Zeebe, self-managed).

<!-- variant:blueprint -->
> [!TIP]
> **This is the stack we recommend** when you are free to choose. Null-safety keeps the domain model and
> the OpenAPI contract precise without annotations, `data` and `value` classes keep the domain compact,
> and Konsist adds source-level architecture rules that bytecode analysis cannot express. The
> [Java + Maven variant](../java-maven/README.md) is functionally identical for teams bound to that stack.
<!-- /variant:blueprint -->

## 🧰 Commands

Run them from this directory. The Camunda 8 stack and Postgres come from
`docker compose -f ../stack/docker-compose.yml up -d`.

| Task | Command |
|---|---|
| Run the service on :8081 | `./gradlew :service:app:bootRun` |
| Full build (arch + unit + process + model validation + spec export) | `./gradlew build` |
| Mutation testing (gate 80) | `./gradlew :service:app:pitest` |
| Regenerate the typed process API after editing a `.bpmn` | `./gradlew generateBpmnModels` |
| Build the OCI image | `./gradlew :service:app:bootBuildImage` |

The process tests start their own Camunda 8 runtime in a container, so the full build needs a running
Docker daemon but not the dev stack.

## 📂 Layout

```
service/
  common-architecture-tests/   reusable ArchUnit + Konsist rule suite (src/main)
  common-zeebe/                Zeebe glue: ProcessEngineApi, BPMN auto-deploy, connection config
  common-zeebe-test/           camunda-process-test helpers (assertions, test engine wiring)
  app/                         the service, package root io.miragon.blueprint
    adapter/inbound/rest        REST controllers + OpenAPI / CORS / problem-details config
    adapter/inbound/zeebe       @JobWorker handlers for the BPMN service tasks
    adapter/outbound/zeebe      drives the engine (ProcessEngineApi / CamundaClient) + task inbox
    adapter/outbound/db         JPA persistence (leasing applications + bike portfolio)
    adapter/outbound/…          simulated dealer / contract / insurance / notification adapters
    adapter/process             generated *ProcessApi + shared constants (bpmn-to-code)
    application/{port,service}  use-case ports and their services
    domain/{leasing,bike}       pure domain model
    resources/{bpmn,dmn,forms}  the process models and Camunda Forms
    resources/db/migration      Flyway versioned schema migrations
```

<!-- variant:blueprint -->
The resources are kept identical to the other variant's; CI fails when they differ.
<!-- /variant:blueprint -->

## 🧱 How it is built

- **Hexagonal architecture.** Domain and use cases never depend on Zeebe, so the business logic is
  testable and the engine replaceable. `common-architecture-tests` enforces it with **ArchUnit**
  (bytecode: layering, dependency direction, naming) and **Konsist** (source: one declaration per file,
  no wildcard imports); a service opts in with `class ArchitectureTest : ServiceArchitectureTest(...)`.
- **Reusable Zeebe glue.** `common-zeebe` contributes the `CamundaClient` connection (self-managed, auth
  off for local dev), a small `ProcessEngineApi` (start instance / publish message) and classpath
  auto-deployment of every `.bpmn`, `.dmn` and `.form` on startup.
- **Job workers, not delegates.** Each BPMN service task is a Spring `@JobWorker` in
  `adapter/inbound/zeebe`. Workers hold no business logic — they call use cases; `orderBike` raises
  the `bikeUnavailable` BPMN error through the job client when the dealer has no bike, so the error
  boundary event catches it.
- **No business key.** Zeebe has none, so the leasing `applicationId` travels as a process variable and
  is the message correlation key on every catch event.
- **Generated process API.** The `bpmn-to-code` Gradle plugin turns each `.bpmn` into a typed,
  node-centric `*ProcessApi` object, so element ids, messages, job types, timers, variables and the
  paths the process tests walk are compile-checked. The output is committed and drift-gated; never
  hand-edit `adapter/process`.
- **Unit tests** (JUnit 5 + MockK) cover every domain type, service, adapter and worker — controllers
  via `@WebMvcTest`, persistence via `@DataJpaTest`.
- **Process tests** (`camunda-process-test`) drive the deployed model on a real in-container Camunda 8
  engine: workers register themselves, timers are advanced with `increaseTime`, and the walked path is
  asserted as a compile-checked `ProcessPath`.
- **Model validation** (`bpmn-to-code-testing`) checks the models structurally at build time for engine
  `ZEEBE`.
- **Mutation testing** (PIT, gate 80) grades assertion strength — diff-scoped on pull requests, full
  sweep nightly.
- **OpenAPI contract.** A test exports the springdoc spec to [`../openapi/openapi.json`](../openapi/openapi.json);
  CI fails on drift.

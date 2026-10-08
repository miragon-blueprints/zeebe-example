# Java + Maven variant

The bike-leasing service in **Java 21**, built with **Maven**, on Spring Boot 4 and Camunda 8.9 (Zeebe,
self-managed). It is the stack most enterprise teams and our trainings use, and needs no Kotlin or
Gradle knowledge.

<!-- variant:blueprint -->
> [!NOTE]
> Functionally identical to the [Kotlin + Gradle variant](../kotlin-gradle/README.md), which is the one
> we recommend when you are free to choose. Same process, same REST contract, same scenarios.
<!-- /variant:blueprint -->

## 🧰 Commands

Run them from this directory; the Maven wrapper is included. The Camunda 8 stack and Postgres come from
`docker compose -f ../stack/docker-compose.yml up -d`.

| Task | Command |
|---|---|
| Run the service on :8081 | `./mvnw -pl service/app -am spring-boot:run` |
| Full build (arch + Checkstyle + unit + process + model validation + spec export) | `./mvnw verify` |
| Mutation testing (gate 80) | `./mvnw -pl service/app -am test-compile org.pitest:pitest-maven:mutationCoverage` |
| Regenerate the typed process API after editing a `.bpmn` | `./mvnw -pl service/app generate-sources` |
| Build the OCI image | `./mvnw -pl service/app -am -DskipTests spring-boot:build-image` |

The process tests start their own Camunda 8 runtime in a container, so the full build needs a running
Docker daemon but not the dev stack.

## 📂 Layout

```
pom.xml                        parent: all versions and plugin management
config/checkstyle/             the two source rules (no wildcard imports, one top-level type per file)
service/
  common-architecture-tests/   reusable ArchUnit rule suite (src/main)
  common-zeebe/                Zeebe glue: ProcessEngineApi, BPMN auto-deploy, connection config
  common-zeebe-test/           camunda-process-test wiring for the test engine
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

- **Hexagonal architecture.** Domain and use cases never depend on Zeebe. `common-architecture-tests`
  enforces layering, dependency direction and naming with **ArchUnit**; **Checkstyle** adds the two
  source rules. A service opts in with `class ArchitectureTest extends ServiceArchitectureTest`.
- **Reusable Zeebe glue.** `common-zeebe` contributes the `CamundaClient` connection (self-managed, auth
  off for local dev), a small `ProcessEngineApi` (start instance / publish message) and classpath
  auto-deployment of every `.bpmn`, `.dmn` and `.form` on startup.
- **Job workers, not delegates.** Each BPMN service task is a Spring `@JobWorker` in
  `adapter/inbound/zeebe`. Workers hold no business logic — they call use cases; `orderBike` raises
  the `bikeUnavailable` BPMN error through the job client when the dealer has no bike, so the error
  boundary event catches it.
- **No business key.** Zeebe has none, so the leasing `applicationId` travels as a process variable and
  is the message correlation key on every catch event.
- **Generated process API.** The `bpmn-to-code` Maven plugin turns each `.bpmn` into a typed,
  node-centric `*ProcessApi` class on every build. The output is committed and drift-gated; never
  hand-edit `adapter/process`.
- **Unit tests** (JUnit 5 + Mockito) cover every domain type, service, adapter and worker — controllers
  via `@WebMvcTest` with `@MockitoBean`, persistence via `@DataJpaTest`.
- **Process tests** (`camunda-process-test`) drive the deployed model on a real in-container Camunda 8
  engine and assert the walked path against the generated API.
- **Model validation** (`bpmn-to-code-testing`) checks the models structurally at build time for engine
  `ZEEBE`.
- **Mutation testing** (PIT, gate 80) — diff-scoped on pull requests (`-DtargetClasses="a.b.*"`), full
  sweep nightly.
- **OpenAPI contract.** A test exports the springdoc spec to [`../openapi/openapi.json`](../openapi/openapi.json);
  CI fails on drift.

<!-- variant:blueprint -->
## 🔀 What differs from the Kotlin variant

Only idioms: **records** and `Optional` instead of `data` classes and nullable types, **Mockito** instead
of MockK, **SLF4J** instead of kotlin-logging, **Checkstyle** instead of Konsist. Records carry no
nullability, so the REST DTOs declare it with `@Schema(requiredMode = …)` / `@Schema(types = …)` to
produce the same contract, and reject a missing required property in their compact constructor.
<!-- /variant:blueprint -->

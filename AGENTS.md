# AGENTS.md

Guidance for AI agents (and humans) working in this repo. This is the real file; `CLAUDE.md` just
imports it.

## Project Overview

A headless **MiraVelo bike-leasing** example: the same BPMN process the sibling engine blueprints
implement, on Camunda 8 / Zeebe, with an enforced hexagonal architecture and a REST API — no frontend.

- **Backend** (`service/app`) — Kotlin / Spring Boot 4, hexagonal, Camunda 8 / Zeebe 8.9 engine
  driven by **job workers** (`@JobWorker`). Package root `io.miragon.blueprint`.
- **The REST surface** is described by `openapi/openapi.json`: springdoc generates it from the
  controllers, and it is **committed and drift-gated** so the checked-in contract can never lie about
  the code. See ADR-0003.

## Development Setup

Two commands to a running stack:

```bash
docker compose -f stack/docker-compose.yml up -d   # Camunda 8 (Zeebe + Operate/Tasklist) + Postgres
./gradlew :service:app:bootRun                      # backend on :8081, connects to Zeebe on :26500
```

### Ports (one source of truth — keep README, this file, `.conductor/settings.toml` in sync)

| What | Port |
|---|---|
| Postgres (app read-model DB) | 5432 |
| Backend REST | 8081 |
| Zeebe gRPC · REST | 26500 · 8080/v2 |
| Camunda 8 web apps (Operate / Tasklist) | 8080 |
| OpenAPI spec · Swagger UI | 8081/v3/api-docs · 8081/swagger-ui.html |
| Actuator (health/liveness/readiness · prometheus) | 8081/actuator |

## Build Commands

| Area | Command |
|---|---|
| Backend (arch + unit + process + model validation + spec export) | `./gradlew build` |
| Mutation testing (gate 80) | `./gradlew :service:app:pitest` |
| Regenerate the typed BPMN process API (after editing a `.bpmn`) | `./gradlew generateBpmnModels` |
| Regenerate + verify the OpenAPI contract | `./gradlew :service:app:test --tests "io.miragon.blueprint.openapi.OpenApiSpecExportTest"` then `git diff --exit-code openapi/openapi.json` |
| API scenarios (running stack) | `cd bruno && npx --yes @usebruno/cli@4.0.0 run . --env local -r` |
| BPMN lint | `npm run lint:bpmn` |
| Backend OCI image | `./gradlew :service:app:bootBuildImage` — [ADR-0011](docs/adr/0011-build-and-deployment-approach.md), CONTRIBUTING "Run it in containers" |

## Architecture — the rules are machine-enforced

The backend's hexagonal rules live in `service/common-architecture-tests` (ArchUnit + Konsist) and
**fail the build**. Read `HexagonalArchitectureTest.kt` and `NamingConventionArchitectureTest.kt`
before writing code. The hard rules:

- **One inbound port per controller.** `onlyFulfilOneUseCase` counts constructor params in
  `application.port.inbound` and fails at >1. An inbox listing + a completion are two controllers.
- **No new top-level `config` package.** The containment rule ignores only *direct* members of the
  root package, so `io.miragon.blueprint.config` would fail. Cross-cutting `@Configuration` (CORS,
  OpenAPI, error handling) goes in `adapter.inbound.rest` — the `Configuration` suffix is whitelisted
  there.
- **`adapter/process` is generated.** Never hand-edit `*ProcessApi.kt` or the shared
  `ServiceTasks`/`Messages`/`ProcessVariables`/`Errors`/`Escalations` files; edit the `.bpmn` and
  re-run `generateBpmnModels`.
- **Suffixes:** inbound port `UseCase|Query`; outbound `Port|Repository|Process`; service
  `Service|Configuration`; `adapter.inbound.rest` `Controller|Dto|Input|Mapper|Configuration`;
  `adapter.inbound.zeebe` `Worker`; `adapter.outbound` `PersistenceAdapter|Adapter|Mapper|Entity|Repository`.
- **Spring Data types stop at the adapter.** Ports own their own `Filter`/`Page`/`Criteria` types.

## BPMN Quality Gates

- `bpmn-to-code` generates typed process constants from the models at build time; the
  `BikeLeasingModelValidationTest` (`BpmnRules.all()` for Zeebe) validates the models' structure.
- The generated `adapter/process` sources are committed and **drift-gated**: `./gradlew build`
  regenerates them in place, and CI runs `git diff --exit-code` on the package, so a `.bpmn` edit
  without a regenerate fails the build — same contract as the OpenAPI spec (ADR-0003).
- Since bpmn-to-code 6 the API is node-centric: `<Process>ProcessApi.FlowNodes.<Node>` carries the
  element (`.id`, `ELEMENT_ID`), its `Variables` and its successors (`Next`). Process tests assert
  the walked path as a compile-checked `ProcessPath` (`common-zeebe-test`'s
  `CamundaAssertExtensions.kt`) instead of hand-maintained element-id lists.
- `bpmnlint` (`bpmnlint:recommended` + `camunda-compat/camunda-cloud-8-9` + `@miragon/rules/all`)
  runs on staged `.bpmn` via `.githooks/pre-commit` (install: `npm run hooks:install`). Element ids
  follow the `flow_`/`event_`/`gateway_`/`serviceTask_` conventions the rules enforce.

## Testing

TDD. Match the test style to the layer:

| Layer | Test style |
|---|---|
| domain | plain unit tests |
| application service | mockk unit tests (mock the ports) |
| `adapter.inbound.rest` | `@WebMvcTest` + MockkBean |
| `adapter.outbound.db` | Spring Data JPA slice tests |
| process end-to-end | Camunda 8 process tests (`@CamundaSpringProcessTest`, in-container Zeebe), paths asserted via `ProcessPath` |

**Mutation testing gates PRs at 80** (`:service:app:pitest`): a test that executes without asserting
will fail CI. Coverage says a line ran; mutation says a test would have noticed. The PR gate runs
**diff-scoped** (only the classes the PR changed, still blocking); the **full-module** gate-80 sweep
runs nightly. See ADR-0004.

## Verify After Each Task (targeted, not a full build)

- Backend service/controller: `./gradlew :service:app:test --tests "*<Name>Test"`
- Architecture only: `./gradlew :service:app:test --tests "io.miragon.blueprint.architecture.*"`
- Contract changed: regenerate the spec, then `git diff --exit-code openapi/openapi.json`

## Working with GitHub

Use the `gh` CLI. Write everything (issues, PRs, commit messages) in **English**. Use
**Conventional Commits** (`feat:`, `fix:`, `test:`, `chore:`, `docs:`, `ci:`, `build:`).

## ADRs

Architecture decisions are recorded in `docs/adr/` (0001–0011). Read them to understand *why* the
repo is shaped this way before proposing structural changes.

## Personality

You are a knowledgeable colleague, not someone who passively takes orders. If something proposed
doesn't look right, suggest corrections, ask critical questions, and push back where needed.
Challenge ideas that could benefit from further improvement or iterative refinement rather than just
accepting them at face value.

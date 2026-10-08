# Contributing

Thanks for your interest in the Camunda 8 / Zeebe bike-leasing blueprint! Contributions of all kinds
are welcome — bug reports, feature ideas, docs, and code.

## Getting started

```bash
git clone git@github.com:miragon-blueprints/zeebe-example.git
cd zeebe-example
npm ci && npm run hooks:install                                # BPMN lint + git hooks
```

You need **JDK 21**, **Node ≥ 22**, and **Docker (or Podman)** for the local Camunda 8 stack and
Postgres.

<!-- variant:blueprint -->
The service exists in two equivalent variants: [`kotlin-gradle/`](kotlin-gradle/README.md) (recommended)
and [`java-maven/`](java-maven/README.md). Run either one.
<!-- /variant:blueprint -->

The build wrapper is included, so nothing else needs installing. Start Camunda 8 (Zeebe +
Operate/Tasklist) and Postgres, then the backend on :8081, which connects to Zeebe on :26500:

```bash
docker compose -f stack/docker-compose.yml up -d
```

<!-- variant:kotlin-gradle -->
```bash
cd kotlin-gradle && ./gradlew :service:app:bootRun
```
<!-- /variant:kotlin-gradle -->
<!-- variant:java-maven -->
```bash
cd java-maven && ./mvnw -pl service/app -am spring-boot:run
```
<!-- /variant:java-maven -->

### Ports

| What | Port |
|---|---|
| Postgres (app read-model DB) | 5432 |
| Backend REST | 8081 |
| Zeebe gRPC · REST | 26500 · 8080/v2 |
| Camunda 8 web apps (Operate / Tasklist) | 8080 |
| OpenAPI spec · Swagger UI | 8081/v3/api-docs · 8081/swagger-ui.html |
| Actuator (health · liveness/readiness · prometheus) | 8081/actuator |

The backend serves its `/api` surface on a single origin, so no CORS code runs on the production path
(a dev-only escape hatch exists under the `dev` profile). Under Conductor the ports are fixed and the
workspace runs `nonconcurrent` (see [ADR-0006](docs/adr/0006-fixed-ports-for-v1-portless-as-the-upgrade.md)).

### Manual smoke test

With the stack and backend running, drive the API scenarios and confirm the operational surface:

```bash
cd bruno && npx --yes @usebruno/cli@4.0.0 run . --env local -r   # the end-to-end API scenarios
```

Confirm <http://localhost:8081/swagger-ui.html>, <http://localhost:8081/actuator/health> (status
`UP`) and the Camunda 8 Operate app at <http://localhost:8080> all load. The 14-day withdrawal-period
timer (ORDERED/HANDED_OVER → ACTIVE) cannot be fast-forwarded over REST on a running broker, so that
final transition is covered by the in-container process test (`increaseTime`) rather than by Bruno.

## Run it in containers

The dev loop above runs the backend from source. To run it as an OCI image instead — built by Spring's
Cloud Native Buildpacks integration, no Dockerfile to maintain — build the image and run it against the
local stack. The rationale is in [ADR-0011](docs/adr/0011-build-and-deployment-approach.md).

Build the backend OCI image; it produces `miravelo/zeebe-example:1.0-SNAPSHOT`:

<!-- variant:kotlin-gradle -->
```bash
(cd kotlin-gradle && ./gradlew :service:app:bootBuildImage)
```
<!-- /variant:kotlin-gradle -->
<!-- variant:java-maven -->
```bash
(cd java-maven && ./mvnw -pl service/app -am -DskipTests spring-boot:build-image)
```
<!-- /variant:java-maven -->

Then run it against the local Camunda 8 + Postgres stack:

```bash
docker compose -f stack/docker-compose.yml up -d
docker run --rm --network host \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/bikeleasing \
  -e SPRING_DATASOURCE_USERNAME=admin -e SPRING_DATASOURCE_PASSWORD=admin \
  miravelo/zeebe-example:1.0-SNAPSHOT
```

**Podman:** the image build needs a Docker-API socket. Expose podman's, then build the image as above:

```bash
podman system service --time=0 unix:///tmp/podman.sock &
export DOCKER_HOST=unix:///tmp/podman.sock
```

**Configuration.** `application.yaml` ships dev defaults; the deploy-relevant values are read from the
environment (they win over the baked defaults):

| Env var | Purpose | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL | `jdbc:postgresql://localhost:5432/bikeleasing` |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | DB credentials | `admin` / `admin` |

> **Not production-hardened.** Override the DB credentials (and point the Camunda client at your own
> cluster) before running anywhere real. Schema is owned by Flyway and Hibernate only validates
> ([ADR-0010](docs/adr/0010-flyway-for-database-migrations.md)), so the Postgres volume persists across
> `down`/`up` — reset it with `docker compose -f stack/docker-compose.yml down -v`.

## Scripts

The build, mutation-testing and code-generation commands are listed in the README next to the code:

<!-- variant:kotlin-gradle -->
- [`kotlin-gradle/README.md`](kotlin-gradle/README.md#-commands)
  <!-- /variant:kotlin-gradle -->
  <!-- variant:java-maven -->
- [`java-maven/README.md`](java-maven/README.md#-commands)
  <!-- /variant:java-maven -->

From the repo root:

```bash
npm run lint:bpmn        # bpmnlint the .bpmn models
```

## Ground rules

- **Start from an issue.** Every change traces back to one — open an issue (or pick an existing one)
  and agree on the approach *before* you write code, then reference it in the PR (`Closes #123`).
  This keeps substantial changes discussed up front and the history navigable.
- **Read [`AGENTS.md`](AGENTS.md) first.** It is the single source of guidance for humans and AI
  agents alike.
- **Conventional Commits.** Commit messages and PR titles follow
  [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`,
  `refactor:`, `test:`, `chore:`). Write everything in **English**.
  <!-- variant:blueprint -->
- **Change both variants together.** A change in behaviour goes into `kotlin-gradle/` *and*
  `java-maven/` in the same PR, with equivalent tests. Changes that only concern one language's idioms
  stay on that side. Models, forms, migrations and `application.yaml` exist in both variants and must
  be byte-identical — copy your change over; the `Blueprint Checks` workflow fails otherwise. See
  [ADR-0013](docs/adr/0013-two-stack-variants-side-by-side-on-main.md).
  <!-- /variant:blueprint -->
- **Keep the gates green.** The architecture, contract-drift and mutation (≥ 80)
  gates run in CI on every PR. They are fitness functions, not style guides — a violation fails the
  build. The mutation gate is **diff-scoped** on PRs (only the classes you changed); the full-module
  gate-80 sweep runs nightly.
- **Add tests.** This is a TDD codebase; match the test style to the layer (see `AGENTS.md`).
  Mutation testing means a test that runs without asserting will fail CI.
- **Changing the API?** Re-export the spec (the `OpenApiSpecExportTest`, which every full build runs)
  so the committed `openapi/openapi.json` contract stays in sync — it is **drift-gated in CI**.
- **Changing the process?** Edit the `.bpmn` model under `service/app/src/main/resources/bpmn`,
  regenerate the typed `*ProcessApi`, and lint it with `npm run lint:bpmn`.
- **Changing the database schema?** Flyway owns it. Add a new forward-only migration
  `V{n}__description.sql` under `service/app/src/main/resources/db/migration/` in the same change as
  the entity edit — never edit an already-applied migration. Hibernate runs `validate`, so a mismatch
  fails startup. See [ADR-0010](docs/adr/0010-flyway-for-database-migrations.md).

## Before opening a PR

<!-- variant:kotlin-gradle -->
```bash
(cd kotlin-gradle && ./gradlew build && ./gradlew :service:app:pitest)   # mutation score >= 80
```
<!-- /variant:kotlin-gradle -->
<!-- variant:java-maven -->
```bash
(cd java-maven && ./mvnw verify \
  && ./mvnw -pl service/app -am test-compile org.pitest:pitest-maven:mutationCoverage)   # mutation score >= 80
```
<!-- /variant:java-maven -->

```bash
git diff --exit-code openapi/openapi.json    # the API contract must not drift
```

All of these run in CI on every pull request (JDK 21 / Node ≥ 22).

## Reporting bugs / requesting features

Open an issue. For a process- or contract-related bug, attaching the relevant `.bpmn` model or the
`openapi.json` diff is the fastest path to a fix.

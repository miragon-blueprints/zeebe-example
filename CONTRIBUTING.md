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

Run the whole stack locally:

```bash
docker compose -f stack/docker-compose.yml up -d   # Camunda 8 (Zeebe + Operate/Tasklist) + Postgres
./gradlew :service:app:bootRun                      # backend on :8081, connects to Zeebe on :26500
```

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
cd bruno && npx --yes @usebruno/cli run . --env local -r   # the end-to-end API scenarios
```

Confirm <http://localhost:8081/swagger-ui.html>, <http://localhost:8081/actuator/health> (status
`UP`) and the Camunda 8 Operate app at <http://localhost:8080> all load. The 14-day withdrawal-period
timer (ORDERED/HANDED_OVER → ACTIVE) cannot be fast-forwarded over REST on a running broker, so that
final transition is covered by the in-container process test (`increaseTime`) rather than by Bruno.

## Run it in containers

The dev loop above runs the backend from source. To run it as an OCI image instead — built by Spring's
Cloud Native Buildpacks integration, no Dockerfile to maintain — build the image and run it against the
local stack. The rationale is in [ADR-0011](docs/adr/0011-build-and-deployment-approach.md).

```bash
# 1. build the backend OCI image (Spring buildpacks — no Dockerfile). Produces miravelo/zeebe-example:1.0-SNAPSHOT
./gradlew :service:app:bootBuildImage

# 2. run it against the local Camunda 8 + Postgres stack
docker compose -f stack/docker-compose.yml up -d
docker run --rm --network host \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/bikeleasing \
  -e SPRING_DATASOURCE_USERNAME=admin -e SPRING_DATASOURCE_PASSWORD=admin \
  miravelo/zeebe-example:1.0-SNAPSHOT
```

**Podman:** `bootBuildImage` needs a Docker-API socket. Expose podman's and point the build at it:

```bash
podman system service --time=0 unix:///tmp/podman.sock &
export DOCKER_HOST=unix:///tmp/podman.sock
./gradlew :service:app:bootBuildImage
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

```bash
./gradlew build                         # arch + unit + process + model validation + spec export
./gradlew :service:app:pitest           # mutation score >= 80
./gradlew generateBpmnModels            # regenerate the typed process API after editing a .bpmn
npm run lint:bpmn                       # bpmnlint the .bpmn models
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
- **Keep the gates green.** The architecture (ArchUnit + Konsist), contract-drift and mutation (≥ 80)
  gates run in CI on every PR. They are fitness functions, not style guides — a violation fails the
  build. The mutation gate is **diff-scoped** on PRs (only the classes you changed); the full-module
  gate-80 sweep runs nightly.
- **Add tests.** This is a TDD codebase; match the test style to the layer (see `AGENTS.md`).
  Mutation testing means a test that runs without asserting will fail CI.
- **Changing the API?** springdoc regenerates the committed `openapi/openapi.json`; run the export
  test and `git diff --exit-code openapi/openapi.json` so the checked-in contract stays in sync.
- **Changing the process?** Edit the `.bpmn`, re-run `./gradlew generateBpmnModels`, and keep
  `npm run lint:bpmn` green.
- **Changing the database schema?** Flyway owns it. Add a new forward-only migration
  `V{n}__description.sql` under `service/app/src/main/resources/db/migration/` in the same change as
  the entity edit — never edit an already-applied migration. Hibernate runs `validate`, so a mismatch
  fails startup. See [ADR-0010](docs/adr/0010-flyway-for-database-migrations.md).

## Before opening a PR

```bash
./gradlew build
git diff --exit-code openapi/openapi.json    # the API contract must not drift
./gradlew :service:app:pitest                # mutation score >= 80
```

All of these run in CI on every pull request (JDK 21 / Node ≥ 22).

## Reporting bugs / requesting features

Open an issue. For a process- or contract-related bug, attaching the relevant `.bpmn` model or the
`openapi.json` diff is the fastest path to a fix.

# 0011 — Build and deployment approach: OCI image via buildpacks

- **Status:** Accepted
- **Date:** 2026-08-20

## Context

`stack/docker-compose.yml` starts the **Camunda 8 self-managed dev stack** (Zeebe + Operate/Tasklist +
Postgres + Elasticsearch) for the local loop, but there was no artifact for the app itself. So the
"build & deployment" dimension every template in this family names was empty: a fork could run the dev
loop (`bootRun` against the dev stack) but had no answer to *"how do I ship this as a container?"*. The
template aims to be production-shaped ([ADR-0008](0008-track-the-latest-major-versions.md),
[ADR-0009](0009-actuator-probes-and-prometheus-metrics.md),
[ADR-0010](0010-flyway-for-database-migrations.md)), so it should hand a fork a runnable image, not
just a stack of dependencies.

The backend is a Spring Boot 4 app, and Spring's Gradle plugin can build an OCI image directly from the
fat jar with Cloud Native Buildpacks — no Dockerfile to write or keep in sync with the JDK.

## Decision

We produce an **OCI image for the backend with Spring's `bootBuildImage`** (buildpacks, no Dockerfile),
and keep `stack/docker-compose.yml` as the local Camunda 8 dev stack.

- **Backend image** — `./gradlew :service:app:bootBuildImage` builds
  `miravelo/zeebe-example:<version>` (`bootBuildImage.imageName` in `service/app/build.gradle.kts`, JVM
  pinned via `BP_JVM_VERSION=21`). Buildpacks give a layered, non-root image with no Dockerfile to
  maintain. A hand-written Dockerfile would only be justified if we needed control buildpacks can't
  give; we don't.
- **Dev stack** — `stack/docker-compose.yml` runs the Camunda 8 broker and web apps plus Postgres and
  Elasticsearch for the local loop. The app connects to that broker as an external engine and to its
  own Postgres read-model; nothing bakes the engine into the app image.
- **Config is environment-overridable** (12-factor): `application.yaml` keeps dev defaults so local
  runs are unchanged, but every deploy-relevant value (the datasource URL/credentials, the Zeebe
  connection) is read from an env var that wins over the baked default.

## Consequences

- **Positive:** `bootBuildImage` produces a runnable, layered container with no Dockerfile to maintain;
  the build & deployment dimension is now filled with a single Gradle command.
- **Negative / trade-offs:** with **podman** the buildpack step needs a Docker-API socket
  (`podman system service` + `DOCKER_HOST`). The image is **not production-hardened** — it carries the
  local-dev defaults from `application.yaml` (datasource credentials, the auth-off Zeebe connection),
  which a real deployment must override.
- **Neutral:** a CI job that builds and publishes the image is a natural follow-up, deferred for now.

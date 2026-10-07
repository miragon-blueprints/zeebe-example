# 0003 — OpenAPI as the checked-in contract

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

The Java backend exposes a REST boundary that any API consumer — another service, a test collection,
a generated client, a human with `curl` — depends on. That boundary can be described two ways: hand-write
a spec and hope the code matches it, or derive the spec from the code. A hand-written spec drifts
silently — the first symptom is a runtime 400 at the caller. We want the contract to be *impossible* to
desync, and we want a new contributor to be able to read it without running anything.

## Decision

The **backend is the single source of truth**, and the contract is **generated but committed**:

1. **springdoc** serves a live OpenAPI document from the annotated controllers
   (`@Operation(operationId = …)` gives each endpoint a stable client method name).
2. `OpenApiSpecExportTest` — a code generator wearing a JUnit costume — fetches `/v3/api-docs`,
   re-serialises it **deterministically** (keys sorted, fixed two-space LF indenter, trailing newline,
   `servers` block dropped so the random test port can't cause churn) and writes
   **`openapi/openapi.json`** at the repo root. It runs inside `./mvnw verify`.
3. CI regenerates the spec and runs **`git diff --exit-code -- openapi/openapi.json`** — a **drift
   gate**. If a controller changed and the committed spec wasn't updated, the build fails.

## Why generated-but-committed beats the alternatives

- **vs. a hand-written spec:** eliminates the drift class entirely — the gate fails the PR, not the
  caller.
- **vs. generated-at-build (not committed):** the committed JSON is reviewable in every PR diff (an API
  change is *visible*), any consumer can read or generate against it without booting the backend, and
  offline/agent workflows keep working. The cost — a checked-in generated file — is paid down by the
  drift gate that keeps it honest.

## Consequences

- **Positive:** one contract, gated against the code; API changes are visible in review; consumers are
  decoupled from a running backend.
- **Negative / trade-offs:** a generated file lives in git; forgetting to regenerate is an *expected*
  failure mode — the gate is what makes that safe, so it must never be disabled.
- **Neutral:** determinism is a hard requirement of the export test — any non-deterministic serialisation
  would make the gate flap.

## Implementation notes

- **Jackson 3 date-time.** Spring Boot 4 ships Jackson 3, which defaults `WRITE_DATES_AS_TIMESTAMPS` on.
  springdoc types the fields as `string/date-time`, so DTO date fields are pinned with
  `@JsonFormat(shape = STRING)` to keep payload and contract in sync.
- **Stable operation ids.** Operation ids are set explicitly (`@Operation(operationId = …)`) so any
  generated client's method names stay clean and stable (e.g. `listLeasingApplications`).

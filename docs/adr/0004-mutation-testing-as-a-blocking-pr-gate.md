# 0004 — Mutation testing as a blocking PR gate

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

Line coverage answers "was this line executed?", not "would a test notice if the code broke?". That
gap matters most for **AI-assisted tests**, which reliably chase coverage while writing weak assertions
(assert-not-null instead of assert-a-value). A blueprint that invites agents to generate tests needs a
gate that grades *assertion strength*, not just execution. Mutation testing is inherently slower than
unit tests, though, so the gate must stay a guardrail, not a tollbooth — it must not become the thing
that stalls merges.

## Decision

We run **PIT (pitest)** as a **blocking gate** with `mutationThreshold = 80`, configured in
`service/app/pom.xml` (`pitest-maven`) and run via
`./mvnw -pl service/app -am test-compile pitest:mutationCoverage`.

- **On PRs it runs diff-scoped.** `.github/workflows/pre-merge.yml` computes the backend `*.java` files
  the PR changed (from `pull_request.base.sha`), maps them to `io.miragon.blueprint.<pkg>.<File>*`, and
  passes them to pitest via `-DtargetClasses`. It blocks the PR on the changed classes' score
  but stays off the critical path, and is skipped when a PR touches no backend code.
- **Nightly runs the full sweep.** `.github/workflows/nightly.yml` mutates the whole
  `io.miragon.blueprint.*` module with no property override — the authoritative gate-80 run — and
  uploads the HTML report as an artifact.
- Both runs **exclude noise**: the generated `adapter.process` package, the Spring bootstrap
  (`BikeLeasingApplication`, `BikeCatalogueSeeder`), the dev-only `DevCorsConfiguration`, and the Zeebe
  glue in `adapter.inbound.zeebe.*` (job workers) and `adapter.outbound.zeebe.*` (message and user-task
  client) — thin code exercised only by the slow process tests.
- The **kill-set** is the fast Mockito / `@WebMvcTest` / `@DataJpaTest` unit tests; the
  `camunda-process-test` integration tests (`process.*`) and the ArchUnit/JavaParser tests
  (`architecture.*`) are excluded from the kill-set — they'd make every run slow and non-deterministic
  without adding mutation signal.

**Why 80 and not 100:** some mutants are *equivalent* — no test can tell them apart from the original.
The one survivor of the full sweep is of that kind: removing the empty-list short-circuit in
`BikePortfolioPersistenceAdapter.findAllByIds` only skips a query whose result is empty anyway. In a
diff-scoped run a single such mutant in a small class already costs several points, so a 100 gate would
fail honest PRs. 80 leaves that headroom while still failing tests that execute without asserting; the
full sweep currently kills 146 of 147 mutants.

**What PIT does not see:** it does not mutate a record's canonical constructor or its generated accessors,
`equals`, `hashCode` and `toString`. Validation therefore never lives inline in a compact constructor —
the value objects call `DomainPreconditions.requireNotBlank` or a static method of their own, which PIT
does mutate.

**What a weak test looks like** (the two patterns the gate caught in this repo's own spike): a test
that asserts too few of a DTO's fields — PIT blanks the unasserted ones (`return ""`) and every test
stays green; and a test that exercises only one branch of a boolean — the *"always return true"*
mutant is then *equivalent* to the original. Both are fixed by asserting **every** mapped field and
**both** outcomes of each branch — one assertion per outcome, not per method.

## Consequences

- **Positive:** AI-generated tests are graded on whether they'd catch a real fault; weak assertions
  surface as surviving mutants with per-mutant, inline feedback — at PR time, on the code the PR
  changed.
- **Negative / trade-offs:** mutation testing is slower than unit tests, hence the diff-scoped PR run
  and the separate nightly full sweep. Gate-80 over a single changed class is stricter granularity than
  over the module, so a PR touching a small class with an equivalent mutant can dip below 80 (fix: assert
  every field and both branches, or exclude the class), and a second, differently-named top-level class in
  the same file would be out of PR scope until the nightly sweep (the JavaParser rule forbids such files).
- **Neutral:** the threshold is a deliberate bar, not a measured ceiling — the code currently scores well
  above it, so raising it is a team decision rather than a tooling one.

## Implementation notes

- `service/app/pom.xml` defaults the `targetClasses` property to `io.miragon.blueprint.*` (no
  `-DtargetClasses` override → full-module scope, used by the nightly sweep and local runs) and sets
  `failWhenNoMutations` to `false` so a PR whose changed classes are all excluded/non-mutable doesn't
  fail the build. The root pom sets `skipPitest=true`; only `service/app` opts in. The report lands in
  `service/app/target/pit-reports/`.
- Do **not** rename the `Mutation testing (PIT, gate 80)` job in `pre-merge.yml` — it is the
  branch-protection required check.

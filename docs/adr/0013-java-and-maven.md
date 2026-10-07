# 0013 — Java 21 and Maven for the backend

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

The backend was written in **Kotlin** and built with **Gradle** (Kotlin DSL, a `libs.versions.toml`
version catalog). We decided to move it to **Java** and **Maven**. This repo is a solution template that
gets forked into project teams, and Java + Maven is the toolchain the Camunda 8 and Spring Boot
documentation and starters assume by default, so a fork no longer has to adopt Kotlin and Gradle before
writing process code. Kotlin also needed compiler plugins (`kotlin-spring`, `kotlin-jpa`) just to make
Spring proxies and JPA entities work.

The port has to keep everything that makes the template valuable: the hexagonal rules and their
machine enforcement ([ADR-0002](0002-hexagonal-architecture-for-the-backend.md),
[ADR-0007](0007-two-architecture-test-tools-archunit-and-konsist.md)), the drift-gated OpenAPI contract
([ADR-0003](0003-openapi-as-the-checked-in-contract.md)), the mutation gate
([ADR-0004](0004-mutation-testing-as-a-blocking-pr-gate.md)) and the buildpack image
([ADR-0011](0011-build-and-deployment-approach.md)).

## Decision

We write the backend in **Java 21** and build it with **Maven** (wrapper `./mvnw`, Maven 3.10). The root
`pom.xml` is the reactor and the parent of the four modules (`service/common-zeebe`,
`service/common-zeebe-test`, `service/common-architecture-tests`, `service/app`). It inherits
`spring-boot-starter-parent` (Boot BOM, Java 21, `-parameters`) and pins every version the BOM does not
manage (or manages too old, like H2) in its own `dependencyManagement`, where Dependabot can see and bump
it. The only overridden BOM property is `kotlin.version`, because bpmn-to-code is compiled against a newer
Kotlin than Boot manages. Domain value objects, DTOs and commands are records. Logging is SLF4J.

Each Gradle or Kotlin mechanism maps as follows:

| Before (Kotlin + Gradle) | Now (Java + Maven) |
|---|---|
| `./gradlew build` · `bootRun` | `./mvnw verify` · `./mvnw -pl service/app -am spring-boot:run` |
| `gradle/libs.versions.toml` + bundles | the root `pom.xml`'s `dependencyManagement` + the Spring Boot BOM |
| `generateBpmnModels` task, Kotlin output | `bpmn-to-code-maven` `generate-bpmn-api`, bound to `generate-sources`, Java output |
| `ProcessPath` Kotlin DSL (`CamundaAssertExtensions.kt`) | bpmn-to-code's `PathWalk` + `common-zeebe-test`'s `ProcessPathIds` |
| Konsist (`KotlinSourceGuidelinesTest`) | JavaParser (`JavaSourceGuidelinesTest`); the ArchUnit rules are ported 1:1 |
| mockk / springmockk (`@MockkBean`) | Mockito / `@MockitoBean` |
| `gradle-pitest-plugin`, `-PmutationTargetClasses`, threads = available CPUs | `pitest-maven` + `pitest-junit5-plugin`, `-DtargetClasses`, `pitest.threads` property (default 4) |
| `org.gradle.test-retry` (3 retries, at most 3 failing tests) | surefire `rerunFailingTestsCount=3` (property; no cap on how many tests may be retried) |
| `bootBuildImage` · `springBoot { buildInfo() }` | `spring-boot:build-image` · the `build-info` goal |
| CI `setup-gradle` · Dependabot `gradle` | `setup-java` with `cache: maven` · Dependabot `maven` |

**The REST contract does not change.** springdoc derived the schemas' `required` lists and their
nullable types (OpenAPI 3.1 `"type": ["string", "null"]`) from Kotlin's type nullability (`String` vs
`String?`). Java has no type-level nullability, so the DTO records declare it explicitly and the
committed `openapi/openapi.json` stays the same. The ADR-0003 drift gate is what proves this.

## Consequences

- **Positive:** the template uses the toolchain most Camunda 8 / Spring Boot teams already know. There
  are no Kotlin compiler plugins to keep in step with Spring and Hibernate. PIT runs on plain Java
  bytecode, so the Kotlin-specific `avoidCallsTo` entries are gone.
- **Negative / trade-offs:** Java is more verbose. Records have no `copy` or default arguments, so state
  transitions copy explicitly and tests use builders (`TestObjectBuilder`). Nullability is no longer
  in the types, so it has to be documented and declared where it matters (DTOs, Javadoc). Konsist's
  source model is replaced by hand-written JavaParser checks. Maven's per-module invocations are wordier
  (`-pl service/app -am`, `-Dsurefire.failIfNoSpecifiedTests=false`).
- **Neutral:** ports, the dev stack, the BPMN models, the Bruno collection and the REST contract are
  unchanged. ADRs 0002, 0003, 0004, 0007, 0008, 0009, 0010 and 0011 were rewritten in place for the new
  toolchain; their decisions stand. ADR-0004 keeps gate 80 and its diff-scoped/nightly split; the full
  sweep kills 146 of 147 mutants (the Kotlin build killed 160 of 183). Java 21 is the JVM the Kotlin build
  already targeted. Moving to a newer Java release is an ordinary bump under
  [ADR-0008](0008-track-the-latest-major-versions.md), not part of this decision.

## Implementation notes

- The root pom sets `spring-boot.run.skip`, `spring-boot.build-image.skip` and `skipPitest` to `true`.
  Only `service/app` flips them to `false`. That lets `-pl service/app -am` run a goal from the root
  without `install`ing the library modules. `pitest-maven` is declared on every module so
  `pitest:mutationCoverage` resolves across the reactor. It needs `test-compile` first:
  `./mvnw -pl service/app -am test-compile pitest:mutationCoverage`. The report is written to
  `service/app/target/pit-reports/`.
- With `-am`, surefire also runs in the upstream modules, which have no matching tests, so a filtered
  run needs `-Dtest=… -Dsurefire.failIfNoSpecifiedTests=false`. The architecture suite alone is
  `-Dtest='ArchitectureTest*'`, which also matches the nested `Dependencies`, `Naming`,
  `CodingGuidelines` and `JavaSource` classes.
- A service wires the suite with
  `class ArchitectureTest extends ServiceArchitectureTest { ArchitectureTest() { super("io.miragon.blueprint"); } }`.
  The JavaParser rules find sources by walking up to the outermost `pom.xml`. They skip `target/`,
  `node_modules/`, hidden directories and the generated `adapter.process` package.
- PIT does not mutate a record's canonical constructor, so value-object validation lives in static
  methods (`DomainPreconditions.requireNotBlank`, `Email`'s own check) that the compact constructors call.
  `requireNotBlank` treats no-break spaces as blank, as Kotlin's `isBlank` did.
- The generated process API lives in `service/app/src/main/java/io/miragon/blueprint/adapter/process`.
  It is still committed and drift-gated in CI.

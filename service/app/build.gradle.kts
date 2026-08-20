import io.miragon.bpmn.adapter.GenerateBpmnModelsTask
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage
import org.springframework.boot.gradle.tasks.bundling.BootJar
import java.math.BigDecimal

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springframework)
    alias(libs.plugins.spring.dependency)
    alias(libs.plugins.bpmnToCode)
    alias(libs.plugins.gradleRetryTesting)
    alias(libs.plugins.pitest)
}

springBoot {
    buildInfo()
}

dependencies {
    implementation(libs.bundles.defaultService)
    implementation(libs.bundles.database)
    implementation(project(":service:common-zeebe"))
    implementation(libs.bpmnToCodeRuntime)
    implementation(libs.springdoc)
    testImplementation(libs.bundles.test)
    testImplementation(libs.bundles.zeebeProcessTest)
    testImplementation(libs.bpmnToCodeTesting)
    testImplementation(project(":service:common-zeebe-test"))
    testImplementation(project(":service:common-architecture-tests"))
    testRuntimeOnly(libs.junitPlatformLauncher)
}

// Generates the typed `*ProcessApi` objects (element ids, messages, timers, variables, …) from the
// BPMN models, so workers and tests reference process elements as compile-checked constants.
tasks.register<GenerateBpmnModelsTask>("generateBpmnModels") {
    baseDir = projectDir.toString()
    filePattern = "src/main/resources/bpmn/*.bpmn"
    outputFolderPath = "$projectDir/src/main/kotlin"
    packagePath = "io.miragon.blueprint.adapter.process"
    outputLanguage = OutputLanguage.KOTLIN
    processEngine = ProcessEngine.ZEEBE
}

tasks.named("classes") {
    dependsOn("generateBpmnModels")
}

tasks.test {
    useJUnitPlatform()
    // In-memory Camunda process tests are mildly async; retry to keep CI stable.
    retry {
        maxRetries.set(3)
        maxFailures.set(3)
        failOnPassedAfterRetry.set(false)
    }
}

// Diff-scoped on PRs (via -PmutationTargetClasses), full sweep nightly. Not attached to `build`.
val mutationTargetClasses = (project.findProperty("mutationTargetClasses") as String?)
    ?.split(",")?.map(String::trim)?.filter(String::isNotEmpty)

pitest {
    junit5PluginVersion.set("1.2.2")
    targetClasses.set(mutationTargetClasses ?: listOf("io.miragon.blueprint.*"))
    targetTests.set(listOf("io.miragon.blueprint.*"))
    failWhenNoMutations.set(false)
    excludedClasses.set(
        listOf(
            // Generated typed process API — not hand-written logic.
            "io.miragon.blueprint.adapter.process.*ProcessApi*",
            // Application bootstrap, outside the hexagonal layers.
            "io.miragon.blueprint.BikeLeasingApplication*",
            "io.miragon.blueprint.BikeCatalogueSeeder*",
            // Dev-only CORS escape hatch.
            "io.miragon.blueprint.adapter.inbound.rest.DevCorsConfiguration*",
            // Zeebe engine glue (job workers, message/user-task client) — exercised by the in-memory
            // process test, not unit-mutated.
            "io.miragon.blueprint.adapter.inbound.zeebe.*",
            "io.miragon.blueprint.adapter.outbound.zeebe.*",
        ),
    )
    excludedTestClasses.set(
        listOf(
            "io.miragon.blueprint.process.*",
            "io.miragon.blueprint.architecture.*",
        ),
    )
    threads.set(Runtime.getRuntime().availableProcessors())
    timeoutFactor.set(BigDecimal("2.0"))
    avoidCallsTo.set(listOf("kotlin.jvm.internal", "mu", "org.slf4j", "io.github.oshai"))
    mutators.set(listOf("DEFAULTS"))
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
    mutationThreshold.set(80)
}

tasks.withType<BootJar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// OCI image for the backend, built by Spring's Cloud Native Buildpacks integration — no Dockerfile to
// maintain (layered, non-root by default). See docs/adr and the "Run it in containers" section of
// CONTRIBUTING.md. Build with `./gradlew :service:app:bootBuildImage`.
tasks.named<BootBuildImage>("bootBuildImage") {
    imageName.set("miravelo/zeebe-example:${project.version}")
    // Pin the JVM the buildpack installs to the version the code targets.
    environment.set(mapOf("BP_JVM_VERSION" to "21"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

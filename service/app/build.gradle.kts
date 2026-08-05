import io.miragon.bpmn.adapter.GenerateBpmnModelsTask
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springframework)
    alias(libs.plugins.spring.dependency)
    alias(libs.plugins.bpmnToCode)
    alias(libs.plugins.gradleRetryTesting)
}

dependencies {
    implementation(libs.bundles.defaultService)
    implementation(libs.bundles.database)
    implementation(project(":service:common-zeebe"))
    implementation(libs.bpmnToCodeRuntime)
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

tasks.withType<BootJar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

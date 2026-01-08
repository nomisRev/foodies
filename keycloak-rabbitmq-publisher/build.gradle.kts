import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.serialization)
    alias(libs.plugins.testballoon)
    id("com.gradleup.shadow") version "8.3.6"
}

kotlin {
    jvmToolchain(21)
    compilerOptions { freeCompilerArgs.add("-Xcontext-parameters") }
}

dependencies {
    compileOnly(libs.keycloak.core)
    compileOnly(libs.keycloak.services)
    compileOnly(libs.keycloak.server.spi)
    compileOnly(libs.keycloak.server.spi.private)

    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(libs.rabbitmq)

    testImplementation(libs.testballoon)
    testImplementation(libs.keycloak.core)
    testImplementation(libs.keycloak.services)
    testImplementation(libs.keycloak.server.spi)
    testImplementation(libs.keycloak.server.spi.private)
    testImplementation(project(":server-shared-test"))
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging { events(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED) }
}
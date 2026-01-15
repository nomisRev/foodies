import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.serialization)
    alias(libs.plugins.testballoon)
    alias(libs.plugins.shadow)
}

kotlin {
    jvmToolchain(21)
    compilerOptions { freeCompilerArgs.add("-Xcontext-parameters") }
}

project.version = "0.0.5"

dependencies {
    compileOnly(libs.keycloak.core)
    compileOnly(libs.keycloak.services)
    compileOnly(libs.keycloak.server.spi)
    compileOnly(libs.keycloak.server.spi.private)

    implementation(project(":keycloak-events"))
    implementation(libs.serialization.json)
    implementation(libs.rabbitmq)

    testImplementation(libs.testballoon)
    testImplementation(libs.keycloak.core)
    testImplementation(libs.keycloak.services)
    testImplementation(libs.keycloak.server.spi)
    testImplementation(libs.keycloak.server.spi.private)
    testImplementation(project(":server-shared-test"))
}

tasks.shadowJar {
    archiveClassifier.set("-all")
    mergeServiceFiles()
    dependencies {
        exclude(dependency("org.slf4j:slf4j-api"))
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging { events(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED) }
}

tasks.register<Exec>("publishImageToLocalRegistry") {
    group = "docker"
    description = "Builds the Keycloak Docker image with the RabbitMQ publisher plugin and pushes it to local registry"

    dependsOn(tasks.shadowJar)

    workingDir(rootProject.projectDir)
    commandLine("docker", "build", "-t", "foodies-keycloak:${project.version}", "-f", "keycloak/Dockerfile", ".")
}
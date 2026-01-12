import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.serialization)
    alias(libs.plugins.testballoon)
}

application {
    mainClass.set("io.ktor.foodies.payment.AppKt")
}

kotlin {
    jvmToolchain(21)
    compilerOptions { freeCompilerArgs.add("-Xcontext-parameters") }
}

ktor {
    docker {
        localImageName = "foodies-payment"
        imageTag = project.version.toString()
    }
}

dependencies {
    // Ktor Server
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.config.yaml)

    // Database
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.datetime)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.flyway.postgresql)

    // RabbitMQ
    implementation(libs.rabbitmq)

    // Shared modules
    implementation(project(":server-shared"))
    implementation(project(":rabbitmq-ext"))
    implementation(project(":keycloak-events"))

    // Logging
    implementation(libs.logback)

    // Health Checks
    implementation(libs.cohort.ktor)
    implementation(libs.cohort.hikari)

    // Testing
    testImplementation(project(":server-shared-test"))
    testImplementation(ktorLibs.server.testHost)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.rabbitmq)
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${libs.versions.kotlinx.coroutines.get()}")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging { events(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED) }
}

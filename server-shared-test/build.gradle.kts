import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("foodies.kotlin-conventions")
}

dependencies {
    implementation(project(":server-shared"))
    implementation(ktorLibs.serialization.kotlinx.json)

    implementation(ktorLibs.client.contentNegotiation)

    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.config.yaml)

    implementation(libs.logback)

    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.exposed.jdbc)

    api(ktorLibs.server.testHost)
    api(libs.testcontainers.postgresql)
    api(libs.rabbitmq)
    api(libs.testcontainers.rabbitmq)
    api(libs.testballoon)
}


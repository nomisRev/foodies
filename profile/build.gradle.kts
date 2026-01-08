import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.serialization)
    alias(libs.plugins.testballoon)
}

application { mainClass = "io.ktor.foodies.server.AppKt" }

kotlin {
    jvmToolchain(21)
    compilerOptions { freeCompilerArgs.add("-Xcontext-parameters") }
}

dependencies {
    implementation(project(":server-shared"))
    implementation(project(":keycloak-events"))
    implementation(ktorLibs.client.apache5)
    implementation(ktorLibs.client.contentNegotiation)

    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.config.yaml)

    implementation(ktorLibs.server.htmlBuilder)
    implementation(ktorLibs.server.htmx)

    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(libs.rabbitmq)

    implementation(libs.logback)

    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.exposed.jdbc)
    implementation(libs.flyway.postgresql)

    testImplementation(project(":server-shared-test"))
    testImplementation(libs.testcontainers.rabbitmq)
    testImplementation(libs.testcontainers.postgresql)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging { events(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED) }
}

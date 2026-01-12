import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.serialization)
    alias(libs.plugins.testballoon)
}

kotlin {
    jvmToolchain(21)
    compilerOptions { freeCompilerArgs.add("-Xcontext-parameters") }
}

dependencies {
    // Ktor Server (for Application context and lifecycle)
    implementation(ktorLibs.server.core)

    // RabbitMQ client
    api(libs.rabbitmq)

    // Serialization
    implementation(libs.serialization.json)

    // Logging
    implementation(libs.logback)

    // Testing
    testImplementation(project(":server-shared-test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging { events(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED) }
}

import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.serialization)
}

application { mainClass = "io.ktor.app.AppKt" }

kotlin { jvmToolchain(21) }

dependencies {
    implementation(ktorLibs.server.netty)
    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    testLogging { events(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED) }
}

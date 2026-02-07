import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.power-assert")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("de.infix.testBalloon")
    id("foodies.spotless-conventions")
}

kotlin {
    jvmToolchain(21)
    compilerOptions { freeCompilerArgs.add("-Xcontext-parameters") }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("de.infix.testBalloon:testBalloon-framework-core:0.8.1-K2.3.0")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging { events(FAILED, PASSED, SKIPPED) }
}

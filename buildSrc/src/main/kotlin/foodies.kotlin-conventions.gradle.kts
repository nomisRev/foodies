plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.power-assert")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("de.infix.testBalloon")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events(
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
        )
    }
}

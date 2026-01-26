import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}

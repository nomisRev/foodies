plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("foodies.spotless-conventions")
}

kotlin { jvmToolchain(21) }

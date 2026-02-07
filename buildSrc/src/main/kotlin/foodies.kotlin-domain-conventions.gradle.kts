plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("foodies.spotless-conventions")
    id("foodies.detekt-conventions")
}

kotlin { jvmToolchain(21) }

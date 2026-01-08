plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.serialization)
}

kotlin {
    jvmToolchain(21)
    compilerOptions { freeCompilerArgs.add("-Xcontext-parameters") }
}

dependencies {
    implementation(libs.serialization.core)
}
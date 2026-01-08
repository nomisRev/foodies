plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.serialization)
    id("com.gradleup.shadow") version "8.3.6"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.keycloak.core)
    compileOnly(libs.keycloak.services)
    compileOnly(libs.keycloak.server.spi)
    compileOnly(libs.keycloak.server.spi.private)

    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(libs.rabbitmq)
}

tasks.shadowJar {
    archiveClassifier.set("") // Replaces the default JAR
    mergeServiceFiles() // Important: merges META-INF/services files
}
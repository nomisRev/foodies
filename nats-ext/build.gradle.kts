plugins {
    id("foodies.kotlin-conventions")
}

dependencies {
    // Ktor Server (for Application context and lifecycle)
    implementation(ktorLibs.server.core)

    // NATS client
    api(libs.nats)

    // Health checks
    api(libs.cohort.ktor)

    // Serialization
    implementation(libs.serialization.json)

    // Logging
    implementation(libs.logback)

    // Testing
    testImplementation(project(":server-shared-test"))
}

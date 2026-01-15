plugins {
    id("foodies.kotlin-conventions")
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


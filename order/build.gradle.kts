plugins {
    id("foodies.kotlin-conventions")
    id("foodies.ktor-service-conventions")
}

application {
    mainClass.set("io.ktor.foodies.order.OrderAppKt")
}
version = "0.0.1"

ktor {
    openApi {
        enabled.set(true)
    }
    docker {
        localImageName = "foodies-order"
        imageTag = project.version.toString()
    }
}

dependencies {
    // Ktor Server
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.callId)
    implementation(ktorLibs.server.routingOpenapi)
    implementation(ktorLibs.openapiSchema)

    // Ktor Client (for Basket service)
    implementation(ktorLibs.client.core)
    implementation(ktorLibs.client.cio)
    implementation(ktorLibs.client.contentNegotiation)

    // Database
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.datetime)
    implementation("org.jetbrains.exposed:exposed-json:${libs.versions.exposed.get()}")
    implementation("org.jetbrains.exposed:exposed-dao:${libs.versions.exposed.get()}")
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation("org.flywaydb:flyway-core:${libs.versions.flyway.get()}")
    implementation(libs.flyway.postgresql)

    // RabbitMQ
    implementation(libs.rabbitmq)

    // Health checks
    implementation(libs.cohort.ktor)
    implementation(libs.cohort.hikari)

    // Shared modules
    implementation(project(":server-shared"))
    implementation(project(":events-user"))
    implementation(project(":rabbitmq-ext"))
    implementation(project(":events-common"))
    implementation(project(":events-order"))
    implementation(project(":events-payment"))
    implementation(project(":events-menu"))

    // Logging
    implementation(libs.logback)

    // Testing
    testImplementation(project(":server-shared-test"))
    testImplementation(ktorLibs.server.testHost)
    testImplementation(ktorLibs.client.contentNegotiation)
    testImplementation(ktorLibs.serialization.kotlinx.json)
    testImplementation("org.testcontainers:testcontainers:${libs.versions.testcontainers.get()}")
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.rabbitmq)
}

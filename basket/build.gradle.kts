plugins {
    id("foodies.kotlin-conventions")
    id("io.ktor.plugin")
    id("foodies.k8s-conventions")
}

application { mainClass = "io.ktor.foodies.basket.AppKt" }
version = "0.0.6"

ktor {
    docker {
        localImageName = "foodies-basket"
        imageTag = project.version.toString()
    }
}

dependencies {
    implementation(project(":server-shared"))
    implementation(project(":rabbitmq-ext"))

    // Ktor Server
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.metrics.micrometer)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.auth.jwt)

    // Ktor Client (for Menu service)
    implementation(ktorLibs.client.core)
    implementation(ktorLibs.client.cio)
    implementation(ktorLibs.client.contentNegotiation)

    // Redis
    implementation(libs.lettuce)

    // Logging
    implementation(libs.logback)

    // Metrics
    implementation(libs.micrometer.core)
    implementation(libs.micrometer.prometheus)

    // Serialization
    implementation(libs.serialization.json)

    implementation(libs.cohort.rabbit)
    api(libs.cohort.ktor)
    api(libs.cohort.lettuce)

    // Testing
    testImplementation(project(":server-shared-test"))
    testImplementation(ktorLibs.server.testHost)
    testImplementation(ktorLibs.client.contentNegotiation)
    testImplementation(ktorLibs.serialization.kotlinx.json)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.redis)
    testImplementation(libs.kotlinx.coroutines.reactor)
}


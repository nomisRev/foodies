plugins {
    id("foodies.kotlin-conventions")
    id("foodies.ktor-service-conventions")
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
    implementation(project(":events-order"))

    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.auth.jwt)

    implementation(ktorLibs.client.core)
    implementation(ktorLibs.client.apache5)
    implementation(ktorLibs.client.contentNegotiation)

    implementation(libs.lettuce)
    implementation(libs.kotlinx.coroutines.reactive)

    implementation(libs.logback)

    implementation(libs.serialization.json)

    api(libs.cohort.ktor)
    api(libs.cohort.lettuce)

    testImplementation(project(":server-shared-test"))
    testImplementation(ktorLibs.server.testHost)
    testImplementation(ktorLibs.client.contentNegotiation)
    testImplementation(ktorLibs.serialization.kotlinx.json)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.redis)
    testImplementation(libs.testcontainers.keycloak)
    testImplementation(libs.kotlinx.coroutines.reactor)
}


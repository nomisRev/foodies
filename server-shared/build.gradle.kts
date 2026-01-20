plugins {
    id("foodies.kotlin-conventions")
}

dependencies {
    implementation(ktorLibs.client.apache5)
    implementation(ktorLibs.client.contentNegotiation)
    implementation(ktorLibs.client.auth)

    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.metrics.micrometer)

    implementation(ktorLibs.server.htmlBuilder)
    implementation(ktorLibs.server.htmx)

    implementation(ktorLibs.serialization.kotlinx.json)

    implementation(libs.logback)

    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.exposed.jdbc)
    implementation(libs.flyway.postgresql)

    api(libs.otel.api)
    api(libs.otel.sdk)
    api(libs.otel.exporter.otlp)
    api(libs.otel.ktor)
    api(libs.otel.hikari)
    api(libs.otel.jdbc)
    api(libs.prometheus)

    testImplementation(project(":server-shared-test"))
    testImplementation(ktorLibs.server.testHost)
    testImplementation(libs.testcontainers.keycloak)
    testImplementation(libs.testballoon)
}


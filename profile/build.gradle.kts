plugins {
    id("foodies.kotlin-conventions")
    id("io.ktor.plugin")
    id("foodies.k8s-conventions")
}

application { mainClass = "io.ktor.foodies.server.AppKt" }
version = "0.0.4"

ktor {
    docker {
        localImageName = "foodies-profile"
        imageTag = project.version.toString()
    }
}

dependencies {
    implementation(project(":server-shared"))
    implementation(project(":keycloak-events"))
    implementation(project(":rabbitmq-ext"))

    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.config.yaml)
    implementation(libs.logback)

    implementation(libs.serialization.json)

    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.exposed.jdbc)
    implementation(libs.flyway.postgresql)

    implementation(libs.cohort.rabbit)
    api(libs.cohort.ktor)
    api(libs.cohort.hikari)

    testImplementation(project(":server-shared-test"))
    testImplementation(libs.testcontainers.rabbitmq)
    testImplementation(libs.testcontainers.postgresql)
}


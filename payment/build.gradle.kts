plugins {
    id("foodies.kotlin-conventions")
    id("foodies.ktor-service-conventions")
}

application {
    mainClass.set("io.ktor.foodies.payment.PaymentAppKt")
}
version = "0.0.3"

ktor {
    openApi {
        enabled.set(true)
    }
    docker {
        localImageName = "foodies-payment"
        imageTag = project.version.toString()
    }
}

dependencies {
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.routingOpenapi)
    implementation(ktorLibs.openapiSchema)
    implementation(ktorLibs.server.config.yaml)

    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.datetime)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.flyway.postgresql)

    implementation(libs.rabbitmq)

    implementation(project(":server-shared"))
    implementation(project(":rabbitmq-ext"))
    implementation(project(":events-user"))
    implementation(project(":events-common"))
    implementation(project(":events-payment"))
    implementation(project(":events-order"))

    implementation(libs.logback)

    implementation(libs.cohort.ktor)
    implementation(libs.cohort.hikari)

    testImplementation(project(":server-shared-test"))
    testImplementation(ktorLibs.server.testHost)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.rabbitmq)
}

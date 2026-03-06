plugins {
    id("foodies.kotlin-conventions")
    id("foodies.kover-conventions")
}

dependencies {
    implementation(ktorLibs.server.core)
    api(libs.rabbitmq)
    api(libs.cohort.api)
    api(libs.serialization.json)
    implementation(libs.logback)

    testImplementation(project(":server-shared-test"))
}

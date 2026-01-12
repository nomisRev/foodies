import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.serialization)
    alias(libs.plugins.testballoon)
}

application { mainClass = "io.ktor.foodies.basket.AppKt" }

kotlin {
    jvmToolchain(21)
    compilerOptions { freeCompilerArgs.add("-Xcontext-parameters") }
}

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
    implementation("io.lettuce:lettuce-core:6.5.5.RELEASE")

    // Logging
    implementation(libs.logback)

    // Serialization
    implementation(libs.serialization.json)

    implementation("com.sksamuel.cohort:cohort-rabbit:2.8.2")
    api("com.sksamuel.cohort:cohort-ktor:2.8.2")
    api("com.sksamuel.cohort:cohort-lettuce:2.8.2")

    // Testing
    testImplementation(project(":server-shared-test"))
    testImplementation(ktorLibs.server.testHost)
    testImplementation(ktorLibs.client.contentNegotiation)
    testImplementation(ktorLibs.serialization.kotlinx.json)
    testImplementation("org.testcontainers:testcontainers:1.21.4")
    testImplementation("com.redis:testcontainers-redis:2.2.4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging { events(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED) }
}

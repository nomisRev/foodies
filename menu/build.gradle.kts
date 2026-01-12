import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.serialization)
    alias(libs.plugins.testballoon)
}

application { mainClass = "io.ktor.foodies.menu.AppKt" }

kotlin {
    jvmToolchain(21)
    compilerOptions { freeCompilerArgs.add("-Xcontext-parameters") }
}

ktor {
    docker {
        localImageName = "foodies-menu"
        imageTag = project.version.toString()
    }
}

dependencies {
    implementation(project(":server-shared"))

    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.serialization.kotlinx.json)

    implementation(libs.logback)

    implementation(libs.serialization.json)

    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.datetime)
    implementation(libs.flyway.postgresql)

    api("com.sksamuel.cohort:cohort-ktor:2.8.2")
    api("com.sksamuel.cohort:cohort-hikari:2.8.2")

    testImplementation(project(":server-shared-test"))
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(ktorLibs.server.testHost)
    testImplementation(ktorLibs.client.contentNegotiation)
    testImplementation(ktorLibs.serialization.kotlinx.json)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging { events(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED) }
}

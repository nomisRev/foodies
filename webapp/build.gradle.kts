import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.serialization)
    alias(libs.plugins.testballoon)
}

application { mainClass = "io.ktor.app.AppKt" }

kotlin {
    jvmToolchain(21)
    compilerOptions { freeCompilerArgs.add("-Xcontext-parameters") }
}

ktor {
    docker {
        localImageName = "foodies-webapp"
        imageTag = project.version.toString()
    }
}

dependencies {
    implementation(project(":server-shared"))
    implementation(ktorLibs.client.apache5)
    implementation(ktorLibs.client.contentNegotiation)

    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.config.yaml)

    implementation(ktorLibs.server.htmlBuilder)
    implementation(ktorLibs.server.htmx)

    implementation(ktorLibs.serialization.kotlinx.json)

    implementation(libs.logback)
    implementation(libs.lettuce)
    implementation(libs.cohort.lettuce)
    implementation(libs.kotlinx.coroutines.reactor)

    api(libs.cohort.ktor)
    api(libs.cohort.hikari)

    testImplementation(project(":server-shared-test"))
    testImplementation(libs.testcontainers.redis)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging { events(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED) }
}

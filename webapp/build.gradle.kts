plugins {
    id("foodies.kotlin-conventions")
    id("foodies.ktor-service-conventions")
}

application { mainClass = "io.ktor.foodies.server.WebAppKt" }
version = "0.0.3"

ktor {
    docker {
        localImageName = "foodies-webapp"
        imageTag = project.version.toString()
    }
}

jib {
    from {
        platforms {
            platform {
                os = "linux"
                architecture = "arm64"
            }
        }
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
    testImplementation(libs.testcontainers.keycloak)
}

tasks.test {
    // Exclude HealthCheckSpec from test execution using fully qualified name
    filter {
        excludeTestsMatching("io.ktor.foodies.server.HealthCheckSpec")
    }
}
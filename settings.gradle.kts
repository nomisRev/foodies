dependencyResolutionManagement {
    @Suppress("UnstableApiUsage") repositories { mavenCentral() }

    versionCatalogs { create("ktorLibs") { from("io.ktor:ktor-version-catalog:3.3.3") } }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

include(":webapp")
include(":server-shared")
include(":server-shared-test")
include(":profile")
include(":keycloak-rabbitmq-publisher")
include(":keycloak-events")

rootProject.name = "foodies"

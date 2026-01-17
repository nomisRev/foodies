dependencyResolutionManagement {
    @Suppress("UnstableApiUsage") repositories { mavenCentral() }

    versionCatalogs { create("ktorLibs") { from("io.ktor:ktor-version-catalog:3.3.3") } }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

include(":webapp")
include(":server-shared")
include(":server-shared-test")
include(":profile")
include(":menu")
include(":basket")
include(":order")
include(":rabbitmq-ext")
include(":keycloak-rabbitmq-publisher")
include(":keycloak-events")
include(":payment")
include(":events-common")
project(":events-common").projectDir = file("events/events-common")
include(":events-order")
project(":events-order").projectDir = file("events/events-order")
include(":events-menu")
project(":events-menu").projectDir = file("events/events-menu")
include(":events-payment")
project(":events-payment").projectDir = file("events/events-payment")
//include(":e2e")

rootProject.name = "foodies"

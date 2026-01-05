dependencyResolutionManagement {
    @Suppress("UnstableApiUsage") repositories { mavenCentral() }

    versionCatalogs { create("ktorLibs") { from("io.ktor:ktor-version-catalog:3.3.3") } }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

include(":server")

rootProject.name = "foodies"

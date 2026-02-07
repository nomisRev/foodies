dependencyResolutionManagement {
    repositories { mavenCentral() }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
        create("ktorLibs") { from("io.ktor:ktor-version-catalog:3.3.3") }
    }
}

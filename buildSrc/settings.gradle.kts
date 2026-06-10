dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
        create("ktorLibs") {
            from("io.ktor:ktor-version-catalog:3.5.6")
        }
    }
}

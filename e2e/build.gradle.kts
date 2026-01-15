plugins {
    id("foodies.kotlin-conventions")
}

kotlin {
    jvmToolchain(21)
    compilerOptions { freeCompilerArgs.add("-Xcontext-parameters") }
}

dependencies {
    implementation(libs.playwright)
    implementation(libs.testballoon)
    implementation(ktorLibs.client.core)
    implementation(ktorLibs.client.cio)
    implementation(libs.logback)
    testImplementation(kotlin("test"))
}

tasks.test {
    systemProperty("playwright.junit.enable-cache", "true")
    dependsOn("installPlaywrightBrowsers")

    // Pass command-line args to tests
    // Usage: ./gradlew :e2e:test -Pargs="--headless=false --webappBaseUrl=http://localhost:8080"
    val testArgs = project.findProperty("args") as? String
    if (testArgs != null) {
        testArgs.split(" ").forEach { arg ->
            if (arg.startsWith("--")) {
                val parts = arg.substring(2).split("=", limit = 2)
                if (parts.size == 2) {
                    systemProperty(parts[0], parts[1])
                }
            }
        }
    }
}

tasks.register<JavaExec>("installPlaywrightBrowsers") {
    group = "playwright"
    description = "Installs Playwright browsers"
    mainClass.set("com.microsoft.playwright.CLI")
    classpath = configurations.runtimeClasspath.get()
    args = listOf("install", "chromium")
}

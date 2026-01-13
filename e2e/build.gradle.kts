import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.testballoon)
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
    useJUnitPlatform()

    // Playwright configuration
    systemProperty("playwright.junit.enable-cache", "true")

    // Set test timeouts
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }

    // Ensure browsers are installed before running tests
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

// Task to install Playwright browsers
tasks.register<JavaExec>("installPlaywrightBrowsers") {
    group = "playwright"
    description = "Installs Playwright browsers"
    mainClass.set("com.microsoft.playwright.CLI")
    classpath = configurations.runtimeClasspath.get()
    args = listOf("install", "chromium")
}

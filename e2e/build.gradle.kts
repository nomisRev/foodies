import java.io.File
import org.gradle.api.tasks.JavaExec // Needed for tasks.named("run", JavaExec::class.java)
import org.gradle.api.tasks.Exec // Needed for tasks.register<Exec>
import org.gradle.api.Project // Explicitly import Project to ensure 'project.exec' is resolved
import org.gradle.api.file.FileCollection

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

// A file to store the PID of the webapp process
val webappPidFile = layout.buildDirectory.file("webapp.pid").get().asFile

// Temporarily change to JavaExec for blocking startup debugging
tasks.register<JavaExec>("startWebappForE2E") {
    group = "e2e setup"
    description = "Starts the webapp for e2e tests (blocking for debugging)"
    dependsOn(":webapp:build")
    // Corrected classpath to include webapp's compiled classes and resources
    classpath = project(":webapp").configurations.runtimeClasspath.get() +
                project.files(project(":webapp").layout.buildDirectory.dir("classes/kotlin/main")) +
                project.files(project(":webapp").layout.buildDirectory.dir("resources/main"))
    mainClass.set("io.ktor.foodies.server.WebAppKt")
    // val buildDirFile = layout.buildDirectory.get().asFile // Corrected: access build directory properly
    standardOutput = System.out // Direct output to console for debugging
    errorOutput = System.err   // Direct error to console for debugging
    doFirst {
        println("Classpath for webapp: ${classpath.asPath}")
    }
    doLast {
        println("Webapp started (blocking). Waiting for 10 seconds for startup.")
        Thread.sleep(10000) // Give it time to start up
    }
}


tasks.register("stopWebappForE2E") {
    group = "e2e setup"
    description = "Stops the webapp process started for e2e tests"
    doLast {
        if (webappPidFile.exists()) {
            val pid = webappPidFile.readText().trim()
            println("Attempting to stop webapp with PID: $pid")
            try {
                // Execute kill command directly using Java's ProcessBuilder
                val processBuilder = ProcessBuilder("kill", pid)
                processBuilder.redirectErrorStream(true) // Merge stderr into stdout
                val process = processBuilder.start()
                val exitCode = process.waitFor() // Wait for the kill command to complete
                if (exitCode != 0) {
                    println("Kill command returned non-zero exit code: $exitCode. Output: ${process.inputStream.bufferedReader().readText()}")
                }
            } catch (e: Exception) {
                println("Error stopping webapp: ${e.message}")
            }
        } else {
            println("Webapp PID file not found. Webapp may not have started or already stopped.")
        }
        webappPidFile.delete() // Clean up the PID file
    }
}

tasks.test {
    systemProperty("playwright.junit.enable-cache", "true")
    dependsOn("installPlaywrightBrowsers")
    dependsOn("startWebappForE2E") // Start webapp before e2e tests

    // Temporarily disable stopWebappForE2E for debugging
    // finalizedBy("stopWebappForE2E")

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
    classpath = sourceSets["test"].runtimeClasspath
    args = listOf("install", "chromium", "--with-deps")
}
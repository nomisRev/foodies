import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.api.tasks.testing.Test

plugins {
    id("foodies.kotlin-conventions")
    id("com.gradleup.shadow")
}

project.version = "0.0.5"

dependencies {
    compileOnly(libs.keycloak.core)
    compileOnly(libs.keycloak.services)
    compileOnly(libs.keycloak.server.spi)
    compileOnly(libs.keycloak.server.spi.private)

    implementation(project(":events-user"))
    implementation(libs.serialization.json)
    implementation(libs.rabbitmq)

    testImplementation(libs.testballoon)
    testImplementation(libs.testcontainers.keycloak)
    testImplementation(libs.keycloak.core)
    testImplementation(libs.keycloak.services)
    testImplementation(libs.keycloak.server.spi)
    testImplementation(libs.keycloak.server.spi.private)
    testImplementation(project(":server-shared-test"))
    testImplementation(libs.playwright)
}

val imageTag = "foodies-keycloak:${project.version}"

tasks {
    shadowJar {
        archiveClassifier.set("all")
        mergeServiceFiles()
        dependencies {
            exclude(dependency("org.slf4j:slf4j-api"))
        }
    }

    val publishImageToLocalRegistry = register<Exec>("publishImageToLocalRegistry") {
        group = "docker"
        description = "Builds the Keycloak Docker image and optionally pushes it to a local registry"

        dependsOn(shadowJar)
        inputs.file(shadowJar.map { it.archiveFile })
        inputs.dir(rootProject.projectDir.resolve("keycloak"))

        workingDir(rootProject.projectDir)
        commandLine("docker", "build", "--build-arg", "JAR_VERSION=${project.version}",
            "-t", imageTag,
            "-f", "keycloak/Dockerfile",
            "."
        )
    }

    val localRestartService by registering(Exec::class) {
        group = "deployment"
        description = "Restarts service in K8s"
        dependsOn(named("publishImageToLocalRegistry"))
        commandLine("kubectl", "rollout", "restart", "deployment/keycloak", "-n", "foodies")
    }

    val localReadinessCheck by registering(Exec::class) {
        group = "deployment"
        description = "Awaits service to become available (readiness probe)"
        commandLine(
            "kubectl",
            "wait",
            "--for=condition=available",
            "--timeout=120s",
            "deployment/keycloak",
            "-n",
            "foodies"
        )
    }

    withType<Test>().configureEach {
        dependsOn(publishImageToLocalRegistry)
        dependsOn("installPlaywrightBrowsers")
        systemProperty("keycloak.image", imageTag)
        systemProperty("headless", project.findProperty("headless") ?: "true")
    }
}

tasks.register<JavaExec>("installPlaywrightBrowsers") {
    group = "playwright"
    description = "Installs Playwright browsers"
    mainClass.set("com.microsoft.playwright.CLI")
    classpath = sourceSets["test"].runtimeClasspath
    args = listOf("install", "chromium", "--with-deps")
}

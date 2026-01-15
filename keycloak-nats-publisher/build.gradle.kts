import org.gradle.kotlin.dsl.provideDelegate

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

    implementation(project(":keycloak-events"))
    implementation(libs.serialization.json)
    implementation(libs.nats)

    testImplementation(libs.testballoon)
    testImplementation(libs.keycloak.core)
    testImplementation(libs.keycloak.services)
    testImplementation(libs.keycloak.server.spi)
    testImplementation(libs.keycloak.server.spi.private)
    testImplementation(project(":server-shared-test"))
}

tasks {
    shadowJar {
        archiveClassifier.set("-all")
        mergeServiceFiles()
        dependencies {
            exclude(dependency("org.slf4j:slf4j-api"))
        }
    }
    register<Exec>("publishImageToLocalRegistry") {
        group = "docker"
        description =
            "Builds the Keycloak Docker image with the NATS publisher plugin and pushes it to local registry"

        dependsOn(shadowJar)

        workingDir(rootProject.projectDir)
        commandLine(
            "docker",
            "build",
            "-t",
            "foodies-keycloak:${project.version}",
            "-f",
            "keycloak/Dockerfile",
            "."
        )
    }

    // TODO: Re-use k8s-conventions
    val localRestartService by registering(Exec::class) {
        group = "deployment"
        description = "Restarts service in K8s"
        dependsOn(named("publishImageToLocalRegistry"))
        commandLine("kubectl", "rollout", "restart", "deployment/keycloak", "-n", "foodies")
    }

    val localReadinessCheck by registering(Exec::class) {
        group = "deployment"
        description = "Awaits service to become available (readiness probe)"
        dependsOn(localRestartService)
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

    register("localDeployK8s") {
        group = "deployment"
        description = "Restarts the service and awaits its availability"

        mustRunAfter(":kustomizeDev")
        dependsOn(localRestartService)
        dependsOn(localReadinessCheck)
    }
}

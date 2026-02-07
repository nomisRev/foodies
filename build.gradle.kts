import com.diffplug.spotless.kotlin.KtfmtStep.TrailingCommaManagementStrategy.COMPLETE

plugins {
    id("org.jetbrains.kotlin.plugin.power-assert")
    id(libs.plugins.spotless.get().pluginId)
}

spotless {
    kotlinGradle {
        target("*.gradle.kts", "buildSrc/*.gradle.kts", "buildSrc/src/**/*.gradle.kts")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle().configure {
            it.setRemoveUnusedImports(true)
            it.setTrailingCommaManagementStrategy(COMPLETE)
        }
    }
}

tasks.register<Exec>("kustomizeDev") {
    group = "deployment"
    description = "Applies Kubernetes manifests using kustomize"

    commandLine("kubectl", "apply", "-k", "k8s/overlays/dev")

    dependsOn(":webapp:publishImageToLocalRegistry")
    dependsOn(":menu:publishImageToLocalRegistry")
    dependsOn(":profile:publishImageToLocalRegistry")
    dependsOn(":basket:publishImageToLocalRegistry")
    dependsOn(":order:publishImageToLocalRegistry")
    dependsOn(":payment:publishImageToLocalRegistry")
    dependsOn(":keycloak-rabbitmq-publisher:publishImageToLocalRegistry")

    inputs
        .dir("k8s")
        .withPropertyName("kubernetesManifests")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

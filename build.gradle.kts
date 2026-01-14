plugins {
    id("org.jetbrains.kotlin.plugin.power-assert")
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

    inputs.dir("k8s")
        .withPropertyName("kubernetesManifests")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

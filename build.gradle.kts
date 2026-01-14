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

fun getNextLocalVersion(): String {
    val props = file("local.properties")
    val current = if (props.exists()) {
        java.util.Properties().apply { props.inputStream().use { load(it) } }
            .getProperty("local.version", "0").toInt()
    } else 0
    val next = current + 1
    props.writeText("local.version=$next")
    return next.toString()
}

val publishToLocalRegistry by tasks.registering {
    group = "docker"
    doFirst { project.version = getNextLocalVersion() }
}

subprojects {
    plugins.withId("io.ktor.plugin") {
        tasks.named("publishImageToLocalRegistry") {
            rootProject.tasks.named("publishToLocalRegistry").get().dependsOn(this)
        }
    }
}

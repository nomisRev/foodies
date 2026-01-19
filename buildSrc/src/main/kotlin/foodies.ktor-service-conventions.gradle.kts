import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.registering

plugins {
    id("io.ktor.plugin")
}

tasks {
    named("publishImageToLocalRegistry") {
        dependsOn("classes")
        inputs.files(tasks.named("classes"))
    }

    val localRestartService by registering(Exec::class) {
        group = "deployment"
        description = "Restarts service in K8s"
        dependsOn(named("publishImageToLocalRegistry"))
        commandLine("kubectl", "rollout", "restart", "deployment/${project.name}", "-n", "foodies")
    }

    val localReadinessCheck by registering(Exec::class) {
        group = "deployment"
        description = "Awaits service to become available (readiness probe)"
        commandLine(
            "kubectl",
            "wait",
            "--for=condition=available",
            "--timeout=120s",
            "deployment/${project.name}",
            "-n",
            "foodies"
        )
    }
}

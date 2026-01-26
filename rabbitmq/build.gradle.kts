project.version = "0.0.1"

val imageTag = "foodies-rabbitmq:${project.version}"

tasks {
    val publishImageToLocalRegistry = register<Exec>("publishImageToLocalRegistry") {
        group = "docker"
        description = "Builds the RabbitMQ Docker image with x-delayed-message plugin"

        inputs.file("Dockerfile")

        workingDir(projectDir)
        commandLine("docker", "build",
            "-t", imageTag,
            "."
        )
    }

    val localRestartService by registering(Exec::class) {
        group = "deployment"
        description = "Restarts RabbitMQ cluster in K8s"
        dependsOn(named("publishImageToLocalRegistry"))
        commandLine("kubectl", "delete", "rabbitmqcluster", "rabbitmq", "-n", "foodies")
    }
}

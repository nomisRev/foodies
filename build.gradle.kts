plugins {
    id("org.jetbrains.kotlin.plugin.power-assert")
    id("org.jetbrains.kotlinx.kover")
}

dependencies {
    kover(project(":basket"))
    kover(project(":menu"))
    kover(project(":order"))
    kover(project(":payment"))
    kover(project(":profile"))
    kover(project(":webapp"))
    kover(project(":server-shared"))
    kover(project(":rabbitmq-ext"))
    kover(project(":keycloak-rabbitmq-publisher"))
}

kover {
    reports {
        total {
            verify {
                rule {
                    minBound(60)
                }
            }
        }
    }
}

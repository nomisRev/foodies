package io.ktor.foodies.keycloak

data class NatsConfig(
    val server: String,
    val stream: String,
    val subject: String
)

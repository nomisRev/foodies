package io.ktor.foodies.keycloak

internal data class RabbitConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val queue: String
)
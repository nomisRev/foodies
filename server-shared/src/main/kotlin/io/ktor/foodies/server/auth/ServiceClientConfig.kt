package io.ktor.foodies.server.auth

import io.ktor.server.config.ApplicationConfig
import kotlinx.serialization.Serializable

@Serializable
data class ServiceClientConfig(
    val issuer: String,
    val clientId: String,
    val clientSecret: String,
    val defaultScopes: List<String> = emptyList()
)

fun ApplicationConfig.serviceClientConfig(path: String): ServiceClientConfig =
    config(path).let { cfg ->
        ServiceClientConfig(
            issuer = cfg.property("issuer").getString(),
            clientId = cfg.property("clientId").getString(),
            clientSecret = cfg.property("clientSecret").getString(),
            defaultScopes = cfg.propertyOrNull("defaultScopes")?.getList() ?: emptyList()
        )
    }

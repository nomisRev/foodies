package io.ktor.foodies.order

import io.ktor.foodies.server.DataSource
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val host: String = "0.0.0.0",
    val port: Int = 8080,
    val auth: AuthConfig,
    val database: DataSource.Config,
    val basket: BasketServiceConfig,
    val rabbit: RabbitConfig,
)

@Serializable
data class AuthConfig(
    val issuer: String,
    val audience: String,
)

@Serializable
data class BasketServiceConfig(
    val baseUrl: String,
)

@Serializable
data class RabbitConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val exchange: String,
    val routingKey: String,
)

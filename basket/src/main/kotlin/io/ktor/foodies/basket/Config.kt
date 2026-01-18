package io.ktor.foodies.basket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val host: String,
    val port: Int,
    val auth: AuthConfig,
    val redis: RedisConfig,
    val menu: MenuConfig,
    val rabbit: RabbitConfig,
    val telemetry: Telemetry,
) {
    @Serializable
    data class Telemetry(
        @SerialName("otlp_endpoint") val otlpEndpoint: String,
    )
}

@Serializable
data class AuthConfig(
    val issuer: String,
    val audience: String,
)

@Serializable
data class RedisConfig(
    val host: String,
    val port: Int,
    val password: String,
)

@Serializable
data class MenuConfig(
    @SerialName("base_url") val baseUrl: String,
)

@Serializable
data class RabbitConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val queue: String,
)

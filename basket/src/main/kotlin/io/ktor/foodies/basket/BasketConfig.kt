package io.ktor.foodies.basket

import io.ktor.foodies.server.openid.Auth
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val host: String,
    val port: Int,
    val auth: Auth,
    val redis: RedisConfig,
    val menu: MenuConfig,
    val rabbit: RabbitConfig,
    val telemetry: Telemetry,
) {
    @Serializable
    data class Telemetry(@SerialName("otlp_endpoint") val otlpEndpoint: String)
}

@Serializable
data class RedisConfig(val host: String, val port: Int, val password: String)

@Serializable
data class MenuConfig(@SerialName("base_url") val baseUrl: String)

@Serializable
data class RabbitConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val exchange: String,
    val queue: String,
)

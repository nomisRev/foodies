package io.ktor.foodies.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val host: String,
    val port: Int,
    val security: Security,
    val menu: Menu,
    val basket: Basket,
    val redis: RedisSession,
    val telemetry: Telemetry,
) {
    @Serializable
    data class Security(val issuer: String, val clientId: String, val clientSecret: String)

    @Serializable
    data class Menu(val baseUrl: String)

    @Serializable
    data class Basket(val baseUrl: String)

    @Serializable
    data class RedisSession(
        val host: String,
        val port: Int,
        val password: String = "",
        val ttlSeconds: Long = 3600
    )

    @Serializable
    data class Telemetry(
        @SerialName("otlp_endpoint") val otlpEndpoint: String,
    )
}

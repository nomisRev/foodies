package io.ktor.foodies.server

import io.ktor.foodies.server.session.RedisSessionConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val host: String,
    val port: Int,
    val security: Security,
    val menu: Menu,
    val basket: Basket,
    val redis: RedisSessionConfig? = null
) {
    @Serializable
    data class Security(val issuer: String, val clientId: String, val clientSecret: String)

    @Serializable
    data class Menu(val baseUrl: String)

    @Serializable
    data class Basket(val baseUrl: String)
}

package io.ktor.foodies.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val host: String,
    val port: Int,
    val security: Security,
    val menu: Menu
) {
    @Serializable
    data class Security(val issuer: String, val clientId: String, val clientSecret: String)

    @Serializable
    data class Menu(val baseUrl: String)
}

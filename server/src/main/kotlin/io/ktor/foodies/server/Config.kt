package io.ktor.foodies.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val host: String,
    val port: Int,
    @SerialName("data_source") val dataSource: DataSource,
    val security: Security
) {
    @Serializable
    data class DataSource(val url: String, val username: String, val password: String)

    @Serializable
    data class Security(val issuer: String, val clientId: String, val clientSecret: String)
}

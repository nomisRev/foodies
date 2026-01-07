package io.ktor.foodies.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val host: String,
    val port: Int,
    @SerialName("data_source") val dataSource: DataSource.Config,
    val security: Security
) {
    @Serializable
    data class Security(val issuer: String, val clientId: String, val clientSecret: String)
}

package io.ktor.foodies.server

import io.ktor.foodies.server.openid.Auth
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val host: String,
    val port: Int,
    val auth: Auth,
    @SerialName("data_source") val dataSource: DataSource.Config,
    val rabbit: Rabbit,
    val telemetry: Telemetry,
) {
    @Serializable
    data class Rabbit(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val queue: String,
    )

    @Serializable
    data class Telemetry(
        @SerialName("otlp_endpoint") val otlpEndpoint: String,
    )
}

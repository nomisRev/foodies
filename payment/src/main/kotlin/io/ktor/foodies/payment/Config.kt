package io.ktor.foodies.payment

import io.ktor.foodies.server.DataSource
import io.ktor.foodies.server.openid.Auth
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val host: String,
    val port: Int,
    val auth: Auth,
    @SerialName("data_source") val dataSource: DataSource.Config,
    val rabbit: RabbitConfig,
    val gateway: PaymentGatewayConfig,
    val telemetry: Telemetry,
) {
    @Serializable
    data class Telemetry(
        @SerialName("otlp_endpoint") val otlpEndpoint: String,
    )
}

@Serializable
data class RabbitConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    @SerialName("consume_queue") val consumeQueue: String,
    @SerialName("publish_exchange") val publishExchange: String,
)

@Serializable
data class PaymentGatewayConfig(
    @SerialName("always_succeed") val alwaysSucceed: Boolean,
    @SerialName("processing_delay_ms") val processingDelayMs: Long,
)

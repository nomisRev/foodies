package io.ktor.foodies.payment

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val host: String,
    val port: Int,
    @SerialName("data_source") val dataSource: DataSourceConfig,
    val rabbit: RabbitConfig,
    val gateway: PaymentGatewayConfig,
)

@Serializable
data class DataSourceConfig(
    val url: String,
    val username: String,
    val password: String,
)

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

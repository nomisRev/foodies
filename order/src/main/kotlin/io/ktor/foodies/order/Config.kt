package io.ktor.foodies.order

import io.ktor.foodies.server.DataSource
import io.ktor.foodies.server.openid.Auth
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val host: String,
    val port: Int,
    val issuer: String,
    val database: DataSource.Config,
    val basket: BasketServiceConfig,
    val payment: PaymentServiceConfig,
    val rabbit: RabbitConfig,
    val order: OrderConfig,
    val telemetry: Telemetry,
)

@Serializable
data class OrderConfig(
    val gracePeriodSeconds: Int,
)

@Serializable
data class BasketServiceConfig(
    val baseUrl: String,
)

@Serializable
data class PaymentServiceConfig(
    val baseUrl: String,
)

@Serializable
data class RabbitConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val exchange: String,
    val routingKey: String,
)

@Serializable
data class Telemetry(
    @SerialName("otlp_endpoint") val otlpEndpoint: String,
)

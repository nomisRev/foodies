package io.ktor.foodies.order

import io.ktor.foodies.server.DataSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val host: String = "0.0.0.0",
    val port: Int = 8084,
    val auth: AuthConfig,
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
data class AuthConfig(
    val issuer: String,
    val audience: String,
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

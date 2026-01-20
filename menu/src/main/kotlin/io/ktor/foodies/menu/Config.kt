package io.ktor.foodies.menu

import io.ktor.foodies.server.DataSource
import io.ktor.foodies.server.telemetry.MonitoringConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val host: String,
    val port: Int,
    @SerialName("data_source") val dataSource: DataSource.Config,
    val rabbit: RabbitConfig,
    val telemetry: MonitoringConfig,
) {
}

@Serializable
data class RabbitConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val exchange: String = "foodies",
    val queue: String = "menu.stock-validation",
)
package io.ktor.foodies.server.telemetry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class MonitoringConfig(@SerialName("otlp_endpoint") val otlpEndpoint: String)

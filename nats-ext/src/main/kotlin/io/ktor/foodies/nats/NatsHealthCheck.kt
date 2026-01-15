package io.ktor.foodies.nats

import com.sksamuel.cohort.HealthCheck
import com.sksamuel.cohort.HealthCheckResult
import io.nats.client.Connection

/**
 * Health check that verifies NATS connection is open and functional.
 */
class NatsConnectionHealthCheck(private val connection: Connection) : HealthCheck {
    override val name: String = "nats-connection"

    override suspend fun check(): HealthCheckResult =
        if (connection.status == Connection.Status.CONNECTED) {
            HealthCheckResult.healthy("NATS connection is connected")
        } else {
            HealthCheckResult.unhealthy("NATS connection is ${connection.status.name.lowercase()}", null)
        }
}

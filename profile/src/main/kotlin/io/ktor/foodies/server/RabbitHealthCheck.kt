package io.ktor.foodies.server

import com.rabbitmq.client.Connection
import com.sksamuel.cohort.HealthCheck
import com.sksamuel.cohort.HealthCheckResult

/**
 * Health check that verifies RabbitMQ connection is open and functional.
 * Uses Cohort's HealthCheck interface for integration with the health check registry.
 */
class RabbitConnectionHealthCheck(private val connection: Connection) : HealthCheck {
    override val name: String = "rabbitmq-connection"

    override suspend fun check(): HealthCheckResult =
        if (connection.isOpen) HealthCheckResult.healthy("RabbitMQ connection is open")
        else HealthCheckResult.unhealthy("RabbitMQ connection is closed", null)
}

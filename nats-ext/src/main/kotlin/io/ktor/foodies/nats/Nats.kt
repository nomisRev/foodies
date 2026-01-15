package io.ktor.foodies.nats

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.nats.client.Connection
import io.nats.client.Nats
import io.nats.client.Options

/**
 * Configuration for NATS connection.
 */
data class NatsConfig(
    val server: String
)

/**
 * Creates a NATS connection with automatic lifecycle management.
 * The connection will be closed when the application stops.
 *
 * @param config The NATS connection configuration
 * @return A NATS connection
 */
context(application: Application)
fun natsConnection(config: NatsConfig): Connection {
    val options = Options.builder()
        .server(config.server)
        .build()
    val connection = Nats.connect(options)
    application.monitor.subscribe(ApplicationStopped) {
        val logger = application.environment.log
        runCatching { connection.close() }
            .onFailure { logger.warn("Failed to close NATS connection", it) }
    }
    return connection
}

package io.ktor.foodies.rabbitmq

import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConnectionFactory
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped

/**
 * Configuration for RabbitMQ connection.
 */
data class RabbitConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String
)

/**
 * Creates a ConnectionFactory configured with the provided settings.
 *
 * @param config The RabbitMQ connection configuration
 * @return A configured ConnectionFactory ready to create connections
 */
fun rabbitConnectionFactory(config: RabbitConfig): ConnectionFactory =
    ConnectionFactory().apply {
        host = config.host
        port = config.port
        username = config.username
        password = config.password
    }

/**
 * Creates a channel with automatic lifecycle management.
 * The channel and its connection will be closed when the application stops.
 *
 * @param queueName The queue name to declare on the channel
 * @param connectionName A name for the connection (for RabbitMQ management UI)
 * @return A channel with the queue declared
 */
context(application: Application)
fun ConnectionFactory.channel(
    queueName: String,
    connectionName: String
): Channel {
    val connection = newConnection(connectionName)
    val channel = connection.createChannel().apply {
        queueDeclare(queueName, true, false, false, null)
    }
    application.monitor.subscribe(ApplicationStopped) {
        val logger = application.environment.log
        runCatching { channel.close() }
            .onFailure { logger.warn("Failed to close RabbitMQ channel", it) }
        runCatching { connection.close() }
            .onFailure { logger.warn("Failed to close RabbitMQ connection", it) }
    }
    return channel
}

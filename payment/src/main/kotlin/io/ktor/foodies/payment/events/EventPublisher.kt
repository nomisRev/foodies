package io.ktor.foodies.payment.events

import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import io.ktor.foodies.payment.RabbitConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

interface EventPublisher {
    suspend fun publish(event: OrderPaymentSucceededEvent)
    suspend fun publish(event: OrderPaymentFailedEvent)
    fun close()
}

class RabbitMQEventPublisher(
    private val config: RabbitConfig
) : EventPublisher {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val factory = ConnectionFactory().apply {
        host = config.host
        port = config.port
        username = config.username
        password = config.password
    }

    private var connection: Connection? = null
    private var channel: Channel? = null

    private fun ensureChannel(): Channel {
        val currentChannel = channel
        if (currentChannel?.isOpen == true) return currentChannel

        val conn = connection?.takeIf { it.isOpen } ?: factory.newConnection().also { connection = it }
        return conn.createChannel().apply {
            exchangeDeclare(config.publishExchange, "topic", true)
            channel = this
        }
    }

    override suspend fun publish(event: OrderPaymentSucceededEvent) {
        publishInternal("payment.succeeded", event)
    }

    override suspend fun publish(event: OrderPaymentFailedEvent) {
        publishInternal("payment.failed", event)
    }

    private inline fun <reified T : Any> publishInternal(routingKey: String, event: T) {
        try {
            val message = Json.encodeToString(event)
            ensureChannel().basicPublish(config.publishExchange, routingKey, null, message.toByteArray())
            logger.info("Published ${T::class.simpleName} to $routingKey")
        } catch (e: Exception) {
            logger.error("Failed to publish ${T::class.simpleName} to $routingKey", e)
            throw e
        }
    }

    override fun close() {
        runCatching { channel?.close() }
        runCatching { connection?.close() }
    }
}

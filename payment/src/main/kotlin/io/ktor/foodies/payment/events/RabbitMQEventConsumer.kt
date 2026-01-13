package io.ktor.foodies.payment.events

import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import io.ktor.foodies.payment.RabbitConfig
import io.ktor.foodies.rabbitmq.messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class RabbitMQEventConsumer(
    private val config: RabbitConfig,
    private val eventHandler: OrderStockConfirmedEventHandler
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val factory = ConnectionFactory().apply {
        host = config.host
        port = config.port
        username = config.username
        password = config.password
    }
    private var connection: Connection? = null
    private var channel: Channel? = null
    private val consumerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        consumerScope.launch {
            try {
                val conn = factory.newConnection().also { connection = it }
                val ch = conn.createChannel().also { channel = it }

                // Declare queue and bind it to exchange
                ch.queueDeclare(config.consumeQueue, true, false, false, null)
                ch.queueBind(config.consumeQueue, config.publishExchange, "order.stock-confirmed")

                logger.info("Started consuming from ${config.consumeQueue}")

                ch.messages<OrderStockConfirmedEvent>(config.consumeQueue).collect { message ->
                    try {
                        logger.info("Received event for order ${message.value.orderId}")
                        eventHandler.handle(message.value)
                        message.ack()
                    } catch (e: Exception) {
                        logger.error("Error handling event for order ${message.value.orderId}: ${e.message}", e)
                        message.nack()
                    }
                }
            } catch (e: Exception) {
                logger.error("Consumer failed: ${e.message}", e)
            }
        }
    }

    fun close() {
        consumerScope.cancel()
        runCatching { channel?.close() }
        runCatching { connection?.close() }
    }
}

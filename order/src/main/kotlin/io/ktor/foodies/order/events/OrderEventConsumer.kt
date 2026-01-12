package io.ktor.foodies.order.events

import com.rabbitmq.client.Channel
import io.ktor.foodies.order.domain.StockConfirmedEvent
import io.ktor.foodies.order.domain.StockRejectedEvent
import io.ktor.foodies.order.events.handlers.StockConfirmedEventHandler
import io.ktor.foodies.order.events.handlers.StockRejectedEventHandler
import io.ktor.foodies.rabbitmq.messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.slf4j.LoggerFactory

class OrderEventConsumer(
    private val channel: Channel,
    private val exchange: String,
    private val stockConfirmedHandler: StockConfirmedEventHandler,
    private val stockRejectedHandler: StockRejectedEventHandler,
    private val scope: CoroutineScope
) {
    private val logger = LoggerFactory.getLogger(OrderEventConsumer::class.java)

    fun start() {
        setupStockConfirmedConsumer()
        setupStockRejectedConsumer()
    }

    private fun setupStockConfirmedConsumer() {
        val queueName = "order.stock-confirmed"
        val routingKey = "stock.confirmed"
        
        channel.queueDeclare(queueName, true, false, false, null)
        channel.queueBind(queueName, exchange, routingKey)

        channel.messages<StockConfirmedEvent>(queueName)
            .onEach { message ->
                runCatching {
                    stockConfirmedHandler.handle(message.value)
                    message.ack()
                }.onFailure { e ->
                    logger.error("Error processing StockConfirmedEvent", e)
                    message.nack()
                }
            }
            .launchIn(scope)
    }

    private fun setupStockRejectedConsumer() {
        val queueName = "order.stock-rejected"
        val routingKey = "stock.rejected"

        channel.queueDeclare(queueName, true, false, false, null)
        channel.queueBind(queueName, exchange, routingKey)

        channel.messages<StockRejectedEvent>(queueName)
            .onEach { message ->
                runCatching {
                    stockRejectedHandler.handle(message.value)
                    message.ack()
                }.onFailure { e ->
                    logger.error("Error processing StockRejectedEvent", e)
                    message.nack()
                }
            }
            .launchIn(scope)
    }
}

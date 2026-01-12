package io.ktor.foodies.order.events

import com.rabbitmq.client.Channel
import io.ktor.foodies.order.domain.*
import io.ktor.foodies.order.events.handlers.*
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
    private val paymentSucceededHandler: PaymentSucceededEventHandler,
    private val paymentFailedHandler: PaymentFailedEventHandler,
    private val orderStatusChangedHandler: OrderStatusChangedEventHandler,
    private val scope: CoroutineScope
) {
    private val logger = LoggerFactory.getLogger(OrderEventConsumer::class.java)

    fun start() {
        setupStockConfirmedConsumer()
        setupStockRejectedConsumer()
        setupPaymentSucceededConsumer()
        setupPaymentFailedConsumer()
        setupOrderStatusChangedConsumer()
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

    private fun setupPaymentSucceededConsumer() {
        val queueName = "order.payment-succeeded"
        val routingKey = "payment.succeeded"

        channel.queueDeclare(queueName, true, false, false, null)
        channel.queueBind(queueName, exchange, routingKey)

        channel.messages<PaymentSucceededEvent>(queueName)
            .onEach { message ->
                runCatching {
                    paymentSucceededHandler.handle(message.value)
                    message.ack()
                }.onFailure { e ->
                    logger.error("Error processing PaymentSucceededEvent", e)
                    message.nack()
                }
            }
            .launchIn(scope)
    }

    private fun setupPaymentFailedConsumer() {
        val queueName = "order.payment-failed"
        val routingKey = "payment.failed"

        channel.queueDeclare(queueName, true, false, false, null)
        channel.queueBind(queueName, exchange, routingKey)

        channel.messages<PaymentFailedEvent>(queueName)
            .onEach { message ->
                runCatching {
                    paymentFailedHandler.handle(message.value)
                    message.ack()
                }.onFailure { e ->
                    logger.error("Error processing PaymentFailedEvent", e)
                    message.nack()
                }
            }
            .launchIn(scope)
    }

    private fun setupOrderStatusChangedConsumer() {
        val queueName = "order.notifications"
        val routingKey = "order.status-changed"

        channel.queueDeclare(queueName, true, false, false, null)
        channel.queueBind(queueName, exchange, routingKey)

        channel.messages<OrderStatusChangedEvent>(queueName)
            .onEach { message ->
                runCatching {
                    orderStatusChangedHandler.handle(message.value)
                    message.ack()
                }.onFailure { e ->
                    logger.error("Error processing OrderStatusChangedEvent", e)
                    message.nack()
                }
            }
            .launchIn(scope)
    }
}

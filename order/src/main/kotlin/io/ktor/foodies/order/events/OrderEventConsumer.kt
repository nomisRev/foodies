package io.ktor.foodies.order.events

import io.ktor.foodies.order.domain.*
import io.ktor.foodies.order.events.handlers.*
import io.ktor.foodies.rabbitmq.RabbitMQSubscriber
import io.ktor.foodies.rabbitmq.consumeMessage
import io.ktor.foodies.rabbitmq.subscribe
import kotlinx.coroutines.flow.Flow

fun orderEventConsumers(
    subscriber: RabbitMQSubscriber,
    exchange: String,
    stockConfirmedHandler: StockConfirmedEventHandler,
    stockRejectedHandler: StockRejectedEventHandler,
    paymentSucceededHandler: PaymentSucceededEventHandler,
    paymentFailedHandler: PaymentFailedEventHandler,
    orderStatusChangedHandler: OrderStatusChangedEventHandler
): List<Flow<Unit>> = listOf(
    run {
        val queueName = "order.stock-confirmed"
        val routingKey = "stock.confirmed"
        subscriber.subscribe<StockConfirmedEvent>(queueName) {
            queueDeclare(queueName, true, false, false, null)
            queueBind(queueName, exchange, routingKey)
        }.consumeMessage { stockConfirmedHandler.handle(it) }
    },
    run {
        val queueName = "order.stock-rejected"
        val routingKey = "stock.rejected"
        subscriber.subscribe<StockRejectedEvent>(queueName) {
            queueDeclare(queueName, true, false, false, null)
            queueBind(queueName, exchange, routingKey)
        }.consumeMessage { stockRejectedHandler.handle(it) }
    },
    run {
        val queueName = "order.payment-succeeded"
        val routingKey = "payment.succeeded"
        subscriber.subscribe<OrderPaymentSucceededEvent>(queueName) {
            queueDeclare(queueName, true, false, false, null)
            queueBind(queueName, exchange, routingKey)
        }.consumeMessage { paymentSucceededHandler.handle(it) }
    },
    run {
        val queueName = "order.payment-failed"
        val routingKey = "payment.failed"
        subscriber.subscribe<OrderPaymentFailedEvent>(queueName) {
            queueDeclare(queueName, true, false, false, null)
            queueBind(queueName, exchange, routingKey)
        }.consumeMessage { paymentFailedHandler.handle(it) }
    },
    run {
        val queueName = "order.notifications"
        val routingKey = "order.status-changed"
        subscriber.subscribe<OrderStatusChangedEvent>(queueName) {
            queueDeclare(queueName, true, false, false, null)
            queueBind(queueName, exchange, routingKey)
        }.consumeMessage { orderStatusChangedHandler.handle(it) }
    }
)

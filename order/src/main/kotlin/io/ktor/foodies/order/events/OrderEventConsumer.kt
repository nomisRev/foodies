package io.ktor.foodies.order.events

import io.ktor.foodies.events.menu.StockConfirmedEvent
import io.ktor.foodies.events.menu.StockRejectedEvent
import io.ktor.foodies.events.payment.OrderPaymentFailedEvent
import io.ktor.foodies.events.payment.OrderPaymentSucceededEvent
import io.ktor.foodies.events.order.*
import io.ktor.foodies.order.domain.*
import io.ktor.foodies.order.events.handlers.*
import io.ktor.foodies.rabbitmq.RabbitMQSubscriber
import io.ktor.foodies.rabbitmq.parConsumeMessage
import io.ktor.foodies.rabbitmq.subscribe
import kotlinx.coroutines.flow.Flow

fun orderEventConsumers(
    subscriber: RabbitMQSubscriber,
    exchange: String,
    stockConfirmedHandler: StockConfirmedEventHandler,
    stockRejectedHandler: StockRejectedEventHandler,
    paymentSucceededHandler: PaymentSucceededEventHandler,
    paymentFailedHandler: PaymentFailedEventHandler,
    orderStatusChangedHandler: OrderStatusChangedEventHandler,
    gracePeriodExpiredHandler: GracePeriodExpiredEventHandler
): List<Flow<Unit>> = listOf(
    run {
        val queueName = "order.stock-confirmed"
        val routingKey = "stock.confirmed"
        subscriber.subscribe<StockConfirmedEvent>(queueName) {
            queueDeclare(queueName, true, false, false, null)
            queueBind(queueName, exchange, routingKey)
        }.parConsumeMessage { stockConfirmedHandler.handle(it) }
    },
    run {
        val queueName = "order.stock-rejected"
        val routingKey = "stock.rejected"
        subscriber.subscribe<StockRejectedEvent>(queueName) {
            queueDeclare(queueName, true, false, false, null)
            queueBind(queueName, exchange, routingKey)
        }.parConsumeMessage { stockRejectedHandler.handle(it) }
    },
    run {
        val queueName = "order.payment-succeeded"
        subscriber.subscribe(OrderPaymentSucceededEvent.key(), queueName) {
            queueDeclare(queueName, true, false, false, null)
            queueBind(queueName, exchange, OrderPaymentSucceededEvent.key().key)
        }.parConsumeMessage { paymentSucceededHandler.handle(it) }
    },
    run {
        val queueName = "order.payment-failed"
        subscriber.subscribe(OrderPaymentFailedEvent.key(), queueName) {
            queueDeclare(queueName, true, false, false, null)
            queueBind(queueName, exchange, OrderPaymentFailedEvent.key().key)
        }.parConsumeMessage { paymentFailedHandler.handle(it) }
    },
    run {
        val queueName = "order.notifications"
        subscriber.subscribe(OrderStatusChangedEvent.key(), queueName) {
            queueDeclare(queueName, true, false, false, null)
            queueBind(queueName, exchange, OrderStatusChangedEvent.key().key)
        }.parConsumeMessage { orderStatusChangedHandler.handle(it) }
    },
    run {
        val queueName = "order.grace-period-expired"
        subscriber.subscribe(GracePeriodExpiredEvent.key(), queueName) {
            val args = mapOf("x-delayed-type" to "direct")
            exchangeDeclare(exchange, "x-delayed-message", true, false, args)

            queueDeclare(queueName, true, false, false, null)
            queueBind(queueName, exchange, GracePeriodExpiredEvent.key().key)
        }.parConsumeMessage { gracePeriodExpiredHandler.handle(it) }
    }
)

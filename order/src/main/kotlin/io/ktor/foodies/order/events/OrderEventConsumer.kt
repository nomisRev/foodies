package io.ktor.foodies.order.events

import io.ktor.foodies.events.menu.StockConfirmedEvent
import io.ktor.foodies.events.menu.StockRejectedEvent
import io.ktor.foodies.events.order.*
import io.ktor.foodies.events.payment.OrderPaymentFailedEvent
import io.ktor.foodies.events.payment.OrderPaymentSucceededEvent
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
    gracePeriodExpiredHandler: GracePeriodExpiredEventHandler,
): List<Flow<Unit>> =
    listOf(
        subscriber.subscribe(StockConfirmedEvent.key(), "order.stock-confirmed").parConsumeMessage {
            stockConfirmedHandler.handle(it)
        },
        subscriber.subscribe(StockRejectedEvent.key(), "order.stock-rejected").parConsumeMessage {
            stockRejectedHandler.handle(it)
        },
        subscriber
            .subscribe(OrderPaymentSucceededEvent.key(), "order.payment-succeeded")
            .parConsumeMessage { paymentSucceededHandler.handle(it) },
        subscriber
            .subscribe(OrderPaymentFailedEvent.key(), "order.payment-failed")
            .parConsumeMessage { paymentFailedHandler.handle(it) },
        subscriber
            .subscribe(OrderStatusChangedEvent.key(), "order.notifications")
            .parConsumeMessage { orderStatusChangedHandler.handle(it) },
        run {
            val queueName = "order.grace-period-expired"
            subscriber
                .subscribe(GracePeriodExpiredEvent.key(), queueName) {
                    val args = mapOf("x-delayed-type" to "direct")
                    exchangeDeclare(exchange, "x-delayed-message", true, false, args)

                    queueDeclare(queueName, true, false, false, null)
                    queueBind(queueName, exchange, GracePeriodExpiredEvent.key().key)
                }
                .parConsumeMessage { gracePeriodExpiredHandler.handle(it) }
        },
    )

package io.ktor.foodies.order.fulfillment

import io.ktor.foodies.order.fulfillment.handlers.GracePeriodExpiredHandler
import io.ktor.foodies.order.fulfillment.handlers.PaymentFailedHandler
import io.ktor.foodies.order.fulfillment.handlers.PaymentSucceededHandler
import io.ktor.foodies.order.fulfillment.handlers.StatusChangedHandler
import io.ktor.foodies.order.fulfillment.handlers.StockConfirmedHandler
import io.ktor.foodies.order.fulfillment.handlers.StockRejectedHandler
import io.ktor.foodies.order.persistence.OrderRepository
import io.ktor.foodies.rabbitmq.Publisher
import io.ktor.foodies.rabbitmq.RabbitMQSubscriber
import kotlinx.coroutines.flow.Flow

data class FulfillmentModule(
    val service: FulfillmentService,
    val consumers: List<Flow<Unit>>,
)

fun fulfillmentModule(
    exchange: String,
    orderRepository: OrderRepository,
    publisher: Publisher,
    subscriber: RabbitMQSubscriber,
): FulfillmentModule {
    val eventPublisher = RabbitFulfillmentEventPublisher(publisher)
    val service = DefaultFulfillmentService(orderRepository, eventPublisher)
    val notificationService = LoggingFulfillmentNotificationService()

    val consumers = fulfillmentEventConsumers(
        subscriber = subscriber,
        exchange = exchange,
        stockConfirmedHandler = StockConfirmedHandler(service),
        stockRejectedHandler = StockRejectedHandler(service),
        paymentSucceededHandler = PaymentSucceededHandler(service),
        paymentFailedHandler = PaymentFailedHandler(service),
        statusChangedHandler = StatusChangedHandler(orderRepository, notificationService),
        gracePeriodExpiredHandler = GracePeriodExpiredHandler(service),
    )

    return FulfillmentModule(service, consumers)
}

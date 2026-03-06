package io.ktor.foodies.payment.events

import io.ktor.foodies.events.payment.OrderPaymentFailedEvent
import io.ktor.foodies.events.payment.OrderPaymentSucceededEvent
import io.ktor.foodies.rabbitmq.Publisher

interface PaymentEventPublisher {
    suspend fun publish(event: OrderPaymentSucceededEvent)
    suspend fun publish(event: OrderPaymentFailedEvent)
}

class RabbitMQPaymentEventPublisher(
    private val publisher: Publisher,
) : PaymentEventPublisher {
    override suspend fun publish(event: OrderPaymentSucceededEvent) = publisher.publish(event)
    override suspend fun publish(event: OrderPaymentFailedEvent) = publisher.publish(event)
}

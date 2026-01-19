package io.ktor.foodies.payment.events

import io.ktor.foodies.events.payment.OrderPaymentFailedEvent
import io.ktor.foodies.events.payment.OrderPaymentSucceededEvent
import io.ktor.foodies.rabbitmq.Publisher
import io.ktor.foodies.rabbitmq.publish

interface EventPublisher {
    suspend fun publish(event: OrderPaymentSucceededEvent)

    suspend fun publish(event: OrderPaymentFailedEvent)
}

class RabbitMQEventPublisher(private val publisher: Publisher) : EventPublisher {
    override suspend fun publish(event: OrderPaymentSucceededEvent) = publisher.publish(event)

    override suspend fun publish(event: OrderPaymentFailedEvent) = publisher.publish(event)
}

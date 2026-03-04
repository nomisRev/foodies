package io.ktor.foodies.order.tracking

import io.ktor.foodies.events.order.OrderCancelledEvent
import io.ktor.foodies.events.order.OrderStatusChangedEvent
import io.ktor.foodies.events.order.StockReturnedEvent
import io.ktor.foodies.rabbitmq.Publisher

interface TrackingEventPublisher {
    suspend fun publish(event: OrderCancelledEvent)
    suspend fun publish(event: StockReturnedEvent)
    suspend fun publish(event: OrderStatusChangedEvent)
}

class RabbitTrackingEventPublisher(
    private val publisher: Publisher,
) : TrackingEventPublisher {
    override suspend fun publish(event: OrderCancelledEvent) = publisher.publish(event)
    override suspend fun publish(event: StockReturnedEvent) = publisher.publish(event)
    override suspend fun publish(event: OrderStatusChangedEvent) = publisher.publish(event)
}

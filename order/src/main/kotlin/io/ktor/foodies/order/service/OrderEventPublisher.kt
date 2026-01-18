package io.ktor.foodies.order.service

import io.ktor.foodies.events.order.*
import io.ktor.foodies.order.domain.*
import io.ktor.foodies.rabbitmq.Publisher
import io.ktor.foodies.rabbitmq.publish

interface OrderEventPublisher {
    suspend fun publish(event: OrderCreatedEvent)
    suspend fun publish(event: OrderCancelledEvent)
    suspend fun publish(event: OrderStatusChangedEvent)
    suspend fun publish(event: OrderStockConfirmedEvent)
    suspend fun publish(event: OrderAwaitingValidationEvent)
    suspend fun publish(event: StockReturnedEvent)
}

class RabbitOrderEventPublisher(
    private val publisher: Publisher,
) : OrderEventPublisher {
    override suspend fun publish(event: OrderCreatedEvent) = publisher.publish(event)
    override suspend fun publish(event: OrderCancelledEvent) = publisher.publish(event)
    override suspend fun publish(event: OrderStatusChangedEvent) = publisher.publish(event)
    override suspend fun publish(event: OrderStockConfirmedEvent) = publisher.publish(event)
    override suspend fun publish(event: OrderAwaitingValidationEvent) = publisher.publish(event)
    override suspend fun publish(event: StockReturnedEvent) = publisher.publish(event)
}

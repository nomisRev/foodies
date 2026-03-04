package io.ktor.foodies.order.fulfillment

import io.ktor.foodies.events.order.OrderAwaitingValidationEvent
import io.ktor.foodies.events.order.OrderCancelledEvent
import io.ktor.foodies.events.order.OrderStatusChangedEvent
import io.ktor.foodies.events.order.OrderStockConfirmedEvent
import io.ktor.foodies.events.order.StockReturnedEvent
import io.ktor.foodies.rabbitmq.Publisher

interface FulfillmentEventPublisher {
    suspend fun publish(event: OrderStatusChangedEvent)
    suspend fun publish(event: OrderAwaitingValidationEvent)
    suspend fun publish(event: OrderStockConfirmedEvent)
    suspend fun publish(event: OrderCancelledEvent)
    suspend fun publish(event: StockReturnedEvent)
}

class RabbitFulfillmentEventPublisher(
    private val publisher: Publisher,
) : FulfillmentEventPublisher {
    override suspend fun publish(event: OrderStatusChangedEvent) = publisher.publish(event)
    override suspend fun publish(event: OrderAwaitingValidationEvent) = publisher.publish(event)
    override suspend fun publish(event: OrderStockConfirmedEvent) = publisher.publish(event)
    override suspend fun publish(event: OrderCancelledEvent) = publisher.publish(event)
    override suspend fun publish(event: StockReturnedEvent) = publisher.publish(event)
}

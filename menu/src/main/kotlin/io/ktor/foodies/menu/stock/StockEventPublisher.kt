package io.ktor.foodies.menu.stock

import io.ktor.foodies.events.menu.StockConfirmedEvent
import io.ktor.foodies.events.menu.StockRejectedEvent
import io.ktor.foodies.rabbitmq.Publisher

interface StockEventPublisher {
    suspend fun publish(event: StockConfirmedEvent)
    suspend fun publish(event: StockRejectedEvent)
}

class RabbitStockEventPublisher(
    private val publisher: Publisher,
) : StockEventPublisher {
    override suspend fun publish(event: StockConfirmedEvent) = publisher.publish(event)
    override suspend fun publish(event: StockRejectedEvent) = publisher.publish(event)
}

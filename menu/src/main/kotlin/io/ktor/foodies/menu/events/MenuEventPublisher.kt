package io.ktor.foodies.menu.events

import io.ktor.foodies.events.menu.StockConfirmedEvent
import io.ktor.foodies.events.menu.StockRejectedEvent
import io.ktor.foodies.rabbitmq.Publisher
import io.ktor.foodies.rabbitmq.publish

interface MenuEventPublisher {
    suspend fun publish(event: StockConfirmedEvent)
    suspend fun publish(event: StockRejectedEvent)
}

class RabbitMenuEventPublisher(
    private val publisher: Publisher,
) : MenuEventPublisher {
    override suspend fun publish(event: StockConfirmedEvent) = publisher.publish(event)
    override suspend fun publish(event: StockRejectedEvent) = publisher.publish(event)
}

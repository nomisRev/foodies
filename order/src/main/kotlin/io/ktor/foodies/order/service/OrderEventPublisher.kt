package io.ktor.foodies.order.service

import com.rabbitmq.client.Channel
import io.ktor.foodies.order.domain.OrderAwaitingValidationEvent
import io.ktor.foodies.order.domain.OrderCancelledEvent
import io.ktor.foodies.order.domain.OrderCreatedEvent
import io.ktor.foodies.order.domain.OrderStockConfirmedEvent
import io.ktor.foodies.order.domain.OrderStatusChangedEvent
import io.ktor.foodies.order.domain.StockReturnedEvent
import io.ktor.foodies.rabbitmq.Publisher
import io.ktor.foodies.rabbitmq.publish
import kotlinx.serialization.json.Json

interface OrderEventPublisher {
    suspend fun publish(event: OrderCreatedEvent)
    suspend fun publish(event: OrderCancelledEvent)
    suspend fun publish(event: OrderStatusChangedEvent)
    suspend fun publish(event: OrderStockConfirmedEvent)
    suspend fun publish(event: OrderAwaitingValidationEvent)
    suspend fun publish(event: StockReturnedEvent)
}

class RabbitOrderEventPublisher(
    private val channel: Channel,
    private val exchange: String,
    private val publisher: Publisher,
    private val orderStatusChangedRoutingKey: String,
    private val orderStockConfirmedRoutingKey: String,
    private val orderAwaitingValidationRoutingKey: String,
    private val stockReturnedRoutingKey: String,
) : OrderEventPublisher {
    override suspend fun publish(event: OrderCreatedEvent) = publisher.publish(event)
    override suspend fun publish(event: OrderCancelledEvent) = publisher.publish(event)

    override suspend fun publish(event: OrderStatusChangedEvent) {
        val message = Json.encodeToString(event)
        channel.basicPublish(exchange, orderStatusChangedRoutingKey, null, message.toByteArray())
    }

    override suspend fun publish(event: OrderStockConfirmedEvent) {
        val message = Json.encodeToString(event)
        channel.basicPublish(exchange, orderStockConfirmedRoutingKey, null, message.toByteArray())
    }

    override suspend fun publish(event: OrderAwaitingValidationEvent) {
        val message = Json.encodeToString(event)
        channel.basicPublish(exchange, orderAwaitingValidationRoutingKey, null, message.toByteArray())
    }

    override suspend fun publish(event: StockReturnedEvent) {
        val message = Json.encodeToString(event)
        channel.basicPublish(exchange, stockReturnedRoutingKey, null, message.toByteArray())
    }
}

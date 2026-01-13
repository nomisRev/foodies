package io.ktor.foodies.menu.events

import com.rabbitmq.client.Channel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface MenuEventPublisher {
    suspend fun publish(event: StockConfirmedEvent)
    suspend fun publish(event: StockRejectedEvent)
}

class RabbitMenuEventPublisher(
    private val channel: Channel,
    private val exchange: String,
    private val stockConfirmedRoutingKey: String = "stock.confirmed",
    private val stockRejectedRoutingKey: String = "stock.rejected",
) : MenuEventPublisher {
    override suspend fun publish(event: StockConfirmedEvent) {
        val message = Json.encodeToString(event)
        channel.basicPublish(exchange, stockConfirmedRoutingKey, null, message.toByteArray())
    }

    override suspend fun publish(event: StockRejectedEvent) {
        val message = Json.encodeToString(event)
        channel.basicPublish(exchange, stockRejectedRoutingKey, null, message.toByteArray())
    }
}

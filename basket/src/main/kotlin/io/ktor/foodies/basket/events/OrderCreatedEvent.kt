package io.ktor.foodies.basket.events

import io.ktor.foodies.rabbitmq.HasRoutingKey
import io.ktor.foodies.server.SerializableBigDecimal
import kotlinx.serialization.Serializable

/**
 * Event received when an order is successfully created by the Ordering service.
 * When this event is received, the basket for the buyer should be cleared.
 */
@Serializable
data class OrderCreatedEvent(
    val orderId: String,
    val buyerId: String,
    val items: List<OrderItem>,
    val totalPrice: SerializableBigDecimal,
    val createdAt: String // ISO-8601 instant string
) : HasRoutingKey {
    override val key: String = "order.created"
}

@Serializable
data class OrderItem(
    val menuItemId: Long,
    val menuItemName: String,
    val unitPrice: SerializableBigDecimal,
    val quantity: Int
)

package io.ktor.foodies.order.domain

import io.ktor.foodies.server.SerializableBigDecimal
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class OrderCreatedEvent(
    val orderId: Long,
    val buyerId: String,
    val items: List<OrderItemSnapshot>,
    val totalPrice: SerializableBigDecimal,
    val createdAt: Instant
)

@Serializable
data class OrderItemSnapshot(
    val menuItemId: Long,
    val quantity: Int,
    val unitPrice: SerializableBigDecimal
)

@Serializable
data class OrderCancelledEvent(
    val orderId: Long,
    val buyerId: String,
    val reason: String,
    val cancelledAt: Instant,
)

@Serializable
data class OrderStatusChangedEvent(
    val orderId: Long,
    val buyerId: String,
    val oldStatus: OrderStatus,
    val newStatus: OrderStatus,
    val description: String?,
    val changedAt: Instant,
)

@Serializable
data class StockConfirmedEvent(
    val orderId: Long,
    val confirmedAt: Instant,
)

@Serializable
data class StockRejectedEvent(
    val orderId: Long,
    val rejectedItems: List<RejectedItem>,
    val rejectedAt: Instant,
)

@Serializable
data class RejectedItem(
    val menuItemId: Long,
    val menuItemName: String,
    val requestedQuantity: Int,
    val availableQuantity: Int,
)

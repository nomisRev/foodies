package io.ktor.foodies.events.order

import io.ktor.foodies.events.common.PaymentMethodInfo
import io.ktor.foodies.rabbitmq.HasRoutingKey
import io.ktor.foodies.server.SerializableBigDecimal
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class OrderStatus {
    Submitted, // Initial state after order creation
    AwaitingValidation, // Grace period ended, awaiting stock validation
    StockConfirmed, // Menu service confirmed item availability
    Paid, // Payment confirmed
    Shipped, // Order shipped to customer
    Cancelled, // Order cancelled (by user, stock rejection, or payment failure)
}

@Serializable
data class OrderCreatedEvent(
    val orderId: Long,
    val buyerId: String,
    val items: List<OrderItemSnapshot>,
    val totalPrice: SerializableBigDecimal,
    val currency: String,
    val createdAt: Instant,
) : HasRoutingKey {
    override val key: String = "order.created"
}

@Serializable
data class OrderItemSnapshot(
    val menuItemId: Long,
    val quantity: Int,
    val unitPrice: SerializableBigDecimal,
)

@Serializable
data class OrderCancelledEvent(
    val orderId: Long,
    val buyerId: String,
    val reason: String,
    val cancelledAt: Instant,
) : HasRoutingKey {
    override val key: String = "order.cancelled"
}

@Serializable
data class OrderStatusChangedEvent(
    val orderId: Long,
    val buyerId: String,
    val oldStatus: OrderStatus,
    val newStatus: OrderStatus,
    val totalPrice: SerializableBigDecimal,
    val currency: String,
    val description: String?,
    val changedAt: Instant,
) : HasRoutingKey {
    override val key: String = "order.status-changed"
}

@Serializable
data class OrderAwaitingValidationEvent(
    val orderId: Long,
    val buyerId: String,
    val items: List<StockValidationItem>,
) : HasRoutingKey {
    override val key: String = "order.awaiting-validation"
}

@Serializable
data class StockReturnedEvent(val orderId: Long, val items: List<StockValidationItem>) :
    HasRoutingKey {
    override val key: String = "order.stock-returned"
}

@Serializable data class StockValidationItem(val menuItemId: Long, val quantity: Int)

@Serializable
data class OrderStockConfirmedEvent(
    val eventId: String,
    val orderId: Long,
    val buyerId: String,
    val totalAmount: SerializableBigDecimal,
    val currency: String,
    val paymentMethod: PaymentMethodInfo,
    val occurredAt: Instant,
) : HasRoutingKey {
    override val key: String = "order.stock-confirmed"
}

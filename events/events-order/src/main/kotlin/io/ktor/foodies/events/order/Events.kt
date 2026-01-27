package io.ktor.foodies.events.order

import io.ktor.foodies.events.common.PaymentMethodInfo
import io.ktor.foodies.rabbitmq.RoutingKey
import io.ktor.foodies.rabbitmq.RoutingKeyOwner
import io.ktor.foodies.server.SerializableBigDecimal
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.serializer

@Serializable
enum class OrderStatus {
    Submitted,              // Initial state after order creation
    AwaitingValidation,     // Grace period ended, awaiting stock validation
    StockConfirmed,         // Menu service confirmed item availability
    Paid,                   // Payment confirmed
    Shipped,                // Order shipped to customer
    Cancelled               // Order cancelled (by user, stock rejection, or payment failure)
}

@Serializable
data class OrderCreatedEvent(
    val orderId: Long,
    val buyerId: String,
    val items: List<OrderItemSnapshot>,
    val totalPrice: SerializableBigDecimal,
    val currency: String,
    val createdAt: Instant
) : RoutingKeyOwner<OrderCreatedEvent> {
    @Transient
    override val routingKey: RoutingKey<OrderCreatedEvent> = key()

    companion object {
        fun key(): RoutingKey<OrderCreatedEvent> = RoutingKey("order.created", serializer())
    }
}

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
) : RoutingKeyOwner<OrderCancelledEvent> {
    @Transient
    override val routingKey: RoutingKey<OrderCancelledEvent> = key()

    companion object {
        fun key(): RoutingKey<OrderCancelledEvent> = RoutingKey("order.cancelled", serializer())
    }
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
) : RoutingKeyOwner<OrderStatusChangedEvent> {
    @Transient
    override val routingKey: RoutingKey<OrderStatusChangedEvent> = key()

    companion object {
        fun key(): RoutingKey<OrderStatusChangedEvent> = RoutingKey("order.status-changed", serializer())
    }
}

@Serializable
data class OrderAwaitingValidationEvent(
    val orderId: Long,
    val buyerId: String,
    val items: List<StockValidationItem>,
) : RoutingKeyOwner<OrderAwaitingValidationEvent> {
    @Transient
    override val routingKey: RoutingKey<OrderAwaitingValidationEvent> = key()

    companion object {
        fun key(): RoutingKey<OrderAwaitingValidationEvent> = RoutingKey("order.awaiting-validation", serializer())
    }
}

@Serializable
data class StockReturnedEvent(
    val orderId: Long,
    val items: List<StockValidationItem>,
) : RoutingKeyOwner<StockReturnedEvent> {
    @Transient
    override val routingKey: RoutingKey<StockReturnedEvent> = key()

    companion object {
        fun key(): RoutingKey<StockReturnedEvent> = RoutingKey("order.stock-returned", serializer())
    }
}

@Serializable
data class StockValidationItem(
    val menuItemId: Long,
    val quantity: Int,
)

@Serializable
data class OrderStockConfirmedEvent(
    val eventId: String,
    val orderId: Long,
    val buyerId: String,
    val totalAmount: SerializableBigDecimal,
    val currency: String,
    val paymentMethod: PaymentMethodInfo,
    val occurredAt: Instant
) : RoutingKeyOwner<OrderStockConfirmedEvent> {
    @Transient
    override val routingKey: RoutingKey<OrderStockConfirmedEvent> = key()

    companion object {
        fun key(): RoutingKey<OrderStockConfirmedEvent> = RoutingKey("order.stock-confirmed", serializer())
    }
}

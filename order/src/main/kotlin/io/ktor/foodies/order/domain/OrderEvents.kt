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
    val currency: String,
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
    val totalPrice: SerializableBigDecimal,
    val currency: String,
    val description: String?,
    val changedAt: Instant,
)

@Serializable
data class OrderAwaitingValidationEvent(
    val orderId: Long,
    val buyerId: String,
    val items: List<StockValidationItem>,
)

@Serializable
data class StockReturnedEvent(
    val orderId: Long,
    val items: List<StockValidationItem>,
)

@Serializable
data class StockValidationItem(
    val menuItemId: Long,
    val quantity: Int,
)

@Serializable
data class StockConfirmedEvent(
    val orderId: Long,
    val confirmedAt: Instant,
)

@Serializable
enum class PaymentMethodType {
    CREDIT_CARD,
    DEBIT_CARD,
    DIGITAL_WALLET,
    BANK_TRANSFER
}

@Serializable
data class PaymentMethodInfo(
    val type: PaymentMethodType,
    val cardLastFour: String?,
    val cardBrand: CardBrand?,
    val cardHolderName: String?,
    val expirationMonth: Int?,
    val expirationYear: Int?
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

@Serializable
data class OrderPaymentSucceededEvent(
    val eventId: String,
    val orderId: Long,
    val paymentId: Long,
    val transactionId: String,
    val amount: SerializableBigDecimal,
    val currency: String,
    val processedAt: Instant,
)

@Serializable
enum class PaymentFailureCode {
    INSUFFICIENT_FUNDS,
    CARD_DECLINED,
    CARD_EXPIRED,
    INVALID_CARD,
    FRAUD_SUSPECTED,
    GATEWAY_ERROR,
    TIMEOUT,
    UNKNOWN
}

@Serializable
data class OrderPaymentFailedEvent(
    val eventId: String,
    val orderId: Long,
    val failureReason: String,
    val failureCode: PaymentFailureCode,
    val occurredAt: Instant,
)

package io.ktor.foodies.order.domain

import io.ktor.foodies.events.common.CardBrand
import io.ktor.foodies.events.order.OrderStatus
import io.ktor.foodies.server.SerializableBigDecimal
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Order(
    val id: Long,
    val requestId: String,
    val buyerId: String, // Keycloak user ID
    val buyerEmail: String, // User email for notifications
    val buyerName: String, // Display name
    val status: OrderStatus,
    val deliveryAddress: Address,
    val items: List<OrderItem>,
    val paymentMethod: PaymentMethod?, // Set after payment verification
    val totalPrice: SerializableBigDecimal,
    val currency: String,
    val description: String?, // Status description (e.g., rejection reason)
    val history: List<OrderHistoryEntry> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class OrderHistoryEntry(
    val id: Long,
    val orderId: Long,
    val status: OrderStatus,
    val description: String?,
    val createdAt: Instant,
)

@Serializable
data class CreateOrderRequest(
    val street: String,
    val city: String,
    val state: String,
    val country: String,
    val zipCode: String,
    val currency: String = "USD",
    val paymentDetails: PaymentDetails,
)

@Serializable
data class PaymentDetails(
    val cardType: CardBrand,
    val cardNumber: String,
    val cardHolderName: String,
    val cardSecurityNumber: String,
    val expirationMonth: Int,
    val expirationYear: Int,
) {
    fun isNotExpired(): Boolean {
        val now =
            try {
                java.time.YearMonth.now()
            } catch (e: Exception) {
                return false
            }
        val expiry =
            try {
                java.time.YearMonth.of(expirationYear, expirationMonth)
            } catch (e: Exception) {
                return false
            }
        return !expiry.isBefore(now)
    }
}

data class CreateOrder(
    val requestId: String,
    val buyerId: String,
    val buyerEmail: String,
    val buyerName: String,
    val deliveryAddress: Address,
    val items: List<CreateOrderItem>,
    val totalPrice: SerializableBigDecimal,
    val currency: String,
    val paymentDetails: PaymentDetails,
)

data class CreateOrderItem(
    val menuItemId: Long,
    val menuItemName: String,
    val pictureUrl: String,
    val unitPrice: SerializableBigDecimal,
    val quantity: Int,
)

@Serializable
data class OrderSummary(
    val id: Long,
    val status: OrderStatus,
    val totalPrice: SerializableBigDecimal,
    val itemCount: Int,
    val description: String?,
    val createdAt: Instant,
)

@Serializable data class CancelOrderRequest(val reason: String)

@Serializable
data class PaginatedOrders(
    val orders: List<OrderSummary>,
    val total: Long,
    val offset: Long,
    val limit: Int,
)

sealed interface GetOrderResult {
    data class Success(val order: Order) : GetOrderResult

    data object NotFound : GetOrderResult

    data object Forbidden : GetOrderResult
}

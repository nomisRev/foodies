package io.ktor.foodies.order

import io.ktor.foodies.events.common.CardBrand
import io.ktor.foodies.events.order.OrderStatus
import io.ktor.foodies.server.SerializableBigDecimal
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Order(
    val id: Long,
    val requestId: String,
    val buyerId: String,
    val buyerEmail: String,
    val buyerName: String,
    val status: OrderStatus,
    val deliveryAddress: Address,
    val items: List<OrderItem>,
    val paymentMethod: PaymentMethod?,
    val totalPrice: SerializableBigDecimal,
    val currency: String,
    val description: String?,
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
data class OrderItem(
    val id: Long,
    val menuItemId: Long,
    val menuItemName: String,
    val pictureUrl: String,
    val unitPrice: SerializableBigDecimal,
    val quantity: Int,
    val discount: SerializableBigDecimal,
)

@Serializable
data class Address(
    val street: String,
    val city: String,
    val state: String,
    val country: String,
    val zipCode: String,
)

@Serializable
data class PaymentMethod(
    val id: Long,
    val cardType: CardBrand,
    val cardHolderName: String,
    val cardNumber: String,
    val expirationMonth: Int,
    val expirationYear: Int,
)

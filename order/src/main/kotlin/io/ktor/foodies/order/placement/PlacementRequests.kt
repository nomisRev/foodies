package io.ktor.foodies.order.placement

import io.ktor.foodies.events.common.CardBrand
import io.ktor.foodies.order.Address
import io.ktor.foodies.order.OrderItem
import io.ktor.foodies.server.SerializableBigDecimal
import kotlinx.serialization.Serializable

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
        val now = try {
            java.time.YearMonth.now()
        } catch (e: Exception) {
            return false
        }
        val expiry = try {
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

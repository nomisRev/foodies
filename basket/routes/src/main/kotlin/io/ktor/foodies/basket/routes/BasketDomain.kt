package io.ktor.foodies.basket.routes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface BasketError {

    @Serializable
    @SerialName("NotFound")
    data class NotFound(val message: String) : BasketError

    @Serializable
    @SerialName("InvalidRequest")
    data class InvalidRequest(val reasons: List<String>) : BasketError
}

@Serializable
data class CustomerBasket(
    val buyerId: String,
    val items: List<BasketItem> = emptyList()
) {
    fun totalPrice(): SerializableBigDecimal = items.sumOf { it.unitPrice * it.quantity.toBigDecimal() }
}

@Serializable
data class BasketItem(
    val id: String,
    val menuItemId: Long,
    val menuItemName: String,
    val menuItemDescription: String,
    val menuItemImageUrl: String,
    val unitPrice: SerializableBigDecimal,
    val quantity: Int
)

@Serializable
data class AddItemRequest(
    val menuItemId: Long,
    val quantity: Int
)

@Serializable
data class UpdateItemQuantityRequest(val quantity: Int)

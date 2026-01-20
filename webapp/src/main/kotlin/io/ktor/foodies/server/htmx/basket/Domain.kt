package io.ktor.foodies.server.htmx.basket

import io.ktor.foodies.server.SerializableBigDecimal
import kotlinx.serialization.Serializable

@Serializable
data class CustomerBasket(
    val buyerId: String,
    val items: List<BasketItem> = emptyList()
)

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
data class UpdateQuantityRequest(val quantity: Int)

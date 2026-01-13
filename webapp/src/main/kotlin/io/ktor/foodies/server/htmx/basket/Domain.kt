package io.ktor.foodies.server.htmx.basket

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.foodies.server.SerializableBigDecimal
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

/**
 * A user's shopping basket containing items to order.
 */
@Serializable
data class CustomerBasket(
    val buyerId: String,
    val items: List<BasketItem> = emptyList()
)

/**
 * Individual item in a basket.
 */
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

/**
 * Request to add an item to the basket.
 */
@Serializable
data class AddItemRequest(
    val menuItemId: Long,
    val quantity: Int
)

/**
 * Request to update the quantity of an item in the basket.
 */
@Serializable
data class UpdateQuantityRequest(
    val quantity: Int
)

package io.ktor.foodies.basket

import io.ktor.foodies.server.SerializableBigDecimal
import io.ktor.foodies.server.validate
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.util.UUID

/**
 * The root aggregate representing a user's shopping basket.
 */
@Serializable
data class CustomerBasket(
    val buyerId: String,
    val items: List<BasketItem> = emptyList()
) {
    fun totalPrice(): SerializableBigDecimal = items.sumOf { it.unitPrice * it.quantity.toBigDecimal() }
}

/**
 * Individual items within a basket.
 * Uses denormalized data from the Menu service for display purposes.
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

// API Request/Response DTOs

@Serializable
data class AddItemRequest(
    val menuItemId: Long,
    val quantity: Int
)

@Serializable
data class UpdateItemQuantityRequest(
    val quantity: Int
)

// Validated domain objects

data class ValidatedAddItem(
    val menuItemId: Long,
    val quantity: Int
)

data class ValidatedUpdateQuantity(
    val quantity: Int
)

fun AddItemRequest.validate(): ValidatedAddItem = validate {
    ValidatedAddItem(
        menuItemId = menuItemId.validate({ it > 0 }) { "menuItemId must be positive" },
        quantity = quantity.validate({ it >= 1 }) { "quantity must be at least 1" }
    )
}

fun UpdateItemQuantityRequest.validate(): ValidatedUpdateQuantity = validate {
    ValidatedUpdateQuantity(
        quantity = quantity.validate({ it >= 1 }) { "quantity must be at least 1" }
    )
}

// Factory for creating basket items

fun createBasketItem(
    menuItemId: Long,
    menuItemName: String,
    menuItemDescription: String,
    menuItemImageUrl: String,
    unitPrice: SerializableBigDecimal,
    quantity: Int
): BasketItem = BasketItem(
    id = UUID.randomUUID().toString(),
    menuItemId = menuItemId,
    menuItemName = menuItemName,
    menuItemDescription = menuItemDescription,
    menuItemImageUrl = menuItemImageUrl,
    unitPrice = unitPrice,
    quantity = quantity
)

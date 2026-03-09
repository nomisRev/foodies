package io.ktor.foodies.basket

import io.ktor.foodies.basket.routes.AddItemRequest
import io.ktor.foodies.basket.routes.UpdateItemQuantityRequest
import io.ktor.foodies.server.validate

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

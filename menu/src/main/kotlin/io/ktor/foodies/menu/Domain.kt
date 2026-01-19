package io.ktor.foodies.menu

import io.ktor.foodies.server.SerializableBigDecimal
import io.ktor.foodies.server.validate
import java.math.BigDecimal
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class MenuItem(
    val id: Long,
    val name: String,
    val description: String,
    val imageUrl: String,
    val price: SerializableBigDecimal,
    val stock: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class MenuItemResponse(
    val id: Long,
    val name: String,
    val description: String,
    val imageUrl: String,
    val price: SerializableBigDecimal,
    val stock: Int,
)

@Serializable
data class CreateMenuItemRequest(
    val name: String,
    val description: String,
    val imageUrl: String,
    val price: SerializableBigDecimal,
    val stock: Int = 0,
)

@Serializable
data class UpdateMenuItemRequest(
    val name: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val price: SerializableBigDecimal? = null,
    val stock: Int? = null,
)

data class CreateMenuItem(
    val name: String,
    val description: String,
    val imageUrl: String,
    val price: SerializableBigDecimal,
    val stock: Int,
)

data class UpdateMenuItem(
    val name: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val price: SerializableBigDecimal? = null,
    val stock: Int? = null,
)

fun CreateMenuItemRequest.validate(): CreateMenuItem = validate {
    CreateMenuItem(
        name = name.validate(String::isNotBlank) { "name must not be blank" },
        description = description.validate(String::isNotBlank) { "description must not be blank" },
        imageUrl = imageUrl.validate(String::isNotBlank) { "imageUrl must not be blank" },
        price = price.validate({ it > BigDecimal.ZERO }) { "price must be greater than 0" },
        stock = stock.validate({ it >= 0 }) { "stock must be 0 or greater" },
    )
}

fun UpdateMenuItemRequest.validate(): UpdateMenuItem = validate {
    UpdateMenuItem(
        name = name?.validate(String::isNotBlank) { "name must not be blank" },
        description = description?.validate(String::isNotBlank) { "description must not be blank" },
        imageUrl = imageUrl?.validate(String::isNotBlank) { "imageUrl must not be blank" },
        price = price?.validate({ it > BigDecimal.ZERO }) { "price must be greater than 0" },
        stock = stock?.validate({ it >= 0 }) { "stock must be 0 or greater" },
    )
}

fun MenuItem.toResponse(): MenuItemResponse =
    MenuItemResponse(
        id = id,
        name = name,
        description = description,
        imageUrl = imageUrl,
        price = price,
        stock = stock,
    )

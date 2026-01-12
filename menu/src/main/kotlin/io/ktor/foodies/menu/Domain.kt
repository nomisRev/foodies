package io.ktor.foodies.menu

import io.ktor.foodies.server.SerializableBigDecimal
import io.ktor.foodies.server.validate
import java.math.BigDecimal
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class Category(
    val id: Long,
    val name: String,
    val description: String,
    val iconUrl: String?,
)

@Serializable
data class CategoryResponse(
    val id: Long,
    val name: String,
    val description: String,
    val iconUrl: String?,
)

@Serializable
data class MenuItem(
    val id: Long,
    val name: String,
    val description: String,
    val imageUrl: String,
    val price: SerializableBigDecimal,
    val categoryId: Long,
    val categoryName: String,
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
    val categoryId: Long,
    val categoryName: String,
)

@Serializable
data class CreateMenuItemRequest(
    val name: String,
    val description: String,
    val imageUrl: String,
    val price: SerializableBigDecimal,
    val categoryId: Long,
)

@Serializable
data class UpdateMenuItemRequest(
    val name: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val price: SerializableBigDecimal? = null,
    val categoryId: Long? = null,
)

data class CreateMenuItem(
    val name: String,
    val description: String,
    val imageUrl: String,
    val price: BigDecimal,
    val categoryId: Long,
)

data class UpdateMenuItem(
    val name: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val price: BigDecimal? = null,
    val categoryId: Long? = null,
)

fun CreateMenuItemRequest.validate(): CreateMenuItem =
    validate {
        CreateMenuItem(
            name = name.validate(String::isNotBlank) { "name must not be blank" },
            description = description.validate(String::isNotBlank) { "description must not be blank" },
            imageUrl = imageUrl.validate(String::isNotBlank) { "imageUrl must not be blank" },
            price = price.validate({ it > BigDecimal.ZERO }) { "price must be greater than 0" },
            categoryId = categoryId,
        )
    }

fun UpdateMenuItemRequest.validate(): UpdateMenuItem =
    validate {
        UpdateMenuItem(
            name = name?.validate(String::isNotBlank) { "name must not be blank" },
            description = description?.validate(String::isNotBlank) { "description must not be blank" },
            imageUrl = imageUrl?.validate(String::isNotBlank) { "imageUrl must not be blank" },
            price = price?.validate({ it > BigDecimal.ZERO }) { "price must be greater than 0" },
            categoryId = categoryId,
        )
    }

fun Category.toResponse(): CategoryResponse = CategoryResponse(
    id = id,
    name = name,
    description = description,
    iconUrl = iconUrl,
)

fun MenuItem.toResponse(): MenuItemResponse = MenuItemResponse(
    id = id,
    name = name,
    description = description,
    imageUrl = imageUrl,
    price = price,
    categoryId = categoryId,
    categoryName = categoryName,
)

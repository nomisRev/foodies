package io.ktor.foodies.menu

import io.ktor.foodies.server.SerializableBigDecimal
import kotlinx.serialization.Serializable
import kotlin.time.Instant

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


fun MenuItem.toResponse(): MenuItemResponse = MenuItemResponse(
    id = id,
    name = name,
    description = description,
    imageUrl = imageUrl,
    price = price,
    stock = stock,
)

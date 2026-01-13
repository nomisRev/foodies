package io.ktor.foodies.order.domain

import io.ktor.foodies.server.SerializableBigDecimal
import kotlinx.serialization.Serializable

@Serializable
data class OrderItem(
    val id: Long,
    val menuItemId: Long,                       // Reference to Menu service
    val menuItemName: String,                   // Denormalized at order time
    val pictureUrl: String,                     // Denormalized at order time
    val unitPrice: SerializableBigDecimal,      // Price at order time (locked in)
    val quantity: Int,                          // Must be >= 1
    val discount: SerializableBigDecimal,
)

package io.ktor.foodies.menu.events

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class OrderAwaitingValidationEvent(
    val orderId: Long,
    val buyerId: String,
    val items: List<StockValidationItem>,
)

@Serializable
data class StockValidationItem(
    val menuItemId: Long,
    val quantity: Int,
)

@Serializable
data class StockConfirmedEvent(
    val orderId: Long,
    val confirmedAt: Instant,
)

@Serializable
data class StockRejectedEvent(
    val orderId: Long,
    val rejectedItems: List<RejectedItem>,
    val rejectedAt: Instant,
)

@Serializable
data class RejectedItem(
    val menuItemId: Long,
    val menuItemName: String,
    val requestedQuantity: Int,
    val availableQuantity: Int,
)

@Serializable
data class StockReturnedEvent(
    val orderId: Long,
    val items: List<StockValidationItem>,
)

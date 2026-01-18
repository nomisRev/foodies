package io.ktor.foodies.menu.events

import io.ktor.foodies.rabbitmq.HasRoutingKey
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class OrderAwaitingValidationEvent(
    val orderId: Long,
    val buyerId: String,
    val items: List<StockValidationItem>,
) : HasRoutingKey {
    override val key: String = "order.awaiting-validation"
}

@Serializable
data class StockValidationItem(
    val menuItemId: Long,
    val quantity: Int,
)

@Serializable
data class StockConfirmedEvent(
    val orderId: Long,
    val confirmedAt: Instant,
) : HasRoutingKey {
    override val key: String = "stock.confirmed"
}

@Serializable
data class StockRejectedEvent(
    val orderId: Long,
    val rejectedItems: List<RejectedItem>,
    val rejectedAt: Instant,
) : HasRoutingKey {
    override val key: String = "stock.rejected"
}

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
) : HasRoutingKey {
    override val key: String = "order.stock-returned"
}

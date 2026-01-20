package io.ktor.foodies.events.menu

import io.ktor.foodies.rabbitmq.HasRoutingKey
import kotlinx.serialization.Serializable
import kotlin.time.Instant

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

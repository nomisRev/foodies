package io.ktor.foodies.events.menu

import io.ktor.foodies.rabbitmq.RoutingKey
import io.ktor.foodies.rabbitmq.RoutingKeyOwner
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.serializer

@Serializable
data class StockConfirmedEvent(
    val orderId: Long,
    val confirmedAt: Instant,
) : RoutingKeyOwner<StockConfirmedEvent> {
    @Transient
    override val routingKey: RoutingKey<StockConfirmedEvent> = key()

    companion object {
        fun key(): RoutingKey<StockConfirmedEvent> = RoutingKey("stock.confirmed", serializer())
    }
}

@Serializable
data class StockRejectedEvent(
    val orderId: Long,
    val rejectedItems: List<RejectedItem>,
    val rejectedAt: Instant,
) : RoutingKeyOwner<StockRejectedEvent> {
    @Transient
    override val routingKey: RoutingKey<StockRejectedEvent> = key()

    companion object {
        fun key(): RoutingKey<StockRejectedEvent> = RoutingKey("stock.rejected", serializer())
    }
}

@Serializable
data class RejectedItem(
    val menuItemId: Long,
    val menuItemName: String,
    val requestedQuantity: Int,
    val availableQuantity: Int,
)

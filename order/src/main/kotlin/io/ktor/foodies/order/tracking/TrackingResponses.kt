package io.ktor.foodies.order.tracking

import io.ktor.foodies.events.order.OrderStatus
import io.ktor.foodies.order.Order
import io.ktor.foodies.server.SerializableBigDecimal
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class OrderSummary(
    val id: Long,
    val status: OrderStatus,
    val totalPrice: SerializableBigDecimal,
    val itemCount: Int,
    val description: String?,
    val createdAt: Instant,
)

@Serializable
data class CancelOrderRequest(
    val reason: String,
)

@Serializable
data class PaginatedOrders(
    val orders: List<OrderSummary>,
    val total: Long,
    val offset: Long,
    val limit: Int,
)

@Serializable
data class CardBrandResponse(
    val id: String,
    val name: String,
)

sealed interface GetOrderResult {
    data class Success(val order: Order) : GetOrderResult
    data object NotFound : GetOrderResult
    data object Forbidden : GetOrderResult
}

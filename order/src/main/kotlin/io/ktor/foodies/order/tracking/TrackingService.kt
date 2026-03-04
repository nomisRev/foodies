package io.ktor.foodies.order.tracking

import io.ktor.foodies.events.order.OrderCancelledEvent
import io.ktor.foodies.events.order.OrderStatus
import io.ktor.foodies.events.order.OrderStatusChangedEvent
import io.ktor.foodies.events.order.StockReturnedEvent
import io.ktor.foodies.events.order.StockValidationItem
import io.ktor.foodies.order.Order
import io.ktor.foodies.order.persistence.OrderRepository
import java.util.UUID
import kotlin.time.Instant

interface TrackingService {
    suspend fun getOrders(buyerId: String, offset: Long, limit: Int, status: OrderStatus? = null): PaginatedOrders
    suspend fun getAllOrders(offset: Long, limit: Int, status: OrderStatus? = null, buyerId: String? = null): PaginatedOrders
    suspend fun getOrder(id: Long, buyerId: String): GetOrderResult
    suspend fun cancelOrder(requestId: UUID, id: Long, buyerId: String, reason: String): Order
}

class DefaultTrackingService(
    private val trackingRepository: TrackingRepository,
    private val orderRepository: OrderRepository,
    private val eventPublisher: TrackingEventPublisher,
) : TrackingService {

    override suspend fun getOrders(buyerId: String, offset: Long, limit: Int, status: OrderStatus?): PaginatedOrders =
        trackingRepository.findByBuyerId(buyerId, offset, limit, status)

    override suspend fun getAllOrders(offset: Long, limit: Int, status: OrderStatus?, buyerId: String?): PaginatedOrders =
        trackingRepository.findAll(offset, limit, status, buyerId)

    override suspend fun getOrder(id: Long, buyerId: String): GetOrderResult =
        orderRepository.findById(id)?.let { order ->
            if (order.buyerId != buyerId) GetOrderResult.Forbidden
            else GetOrderResult.Success(order)
        } ?: GetOrderResult.NotFound

    override suspend fun cancelOrder(requestId: UUID, id: Long, buyerId: String, reason: String): Order {
        val order = when (val result = getOrder(id, buyerId)) {
            is GetOrderResult.Success -> result.order
            is GetOrderResult.NotFound -> throw IllegalArgumentException("Order not found")
            is GetOrderResult.Forbidden -> throw IllegalArgumentException("Access denied to order")
        }

        if (order.status == OrderStatus.Cancelled) return order

        if (order.status !in listOf(
                OrderStatus.Submitted,
                OrderStatus.AwaitingValidation,
                OrderStatus.StockConfirmed
            )
        ) {
            throw IllegalArgumentException("Cannot cancel order in ${order.status} status")
        }

        val oldStatus = order.status
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val cancelledOrder = order.copy(
            status = OrderStatus.Cancelled,
            description = reason,
            updatedAt = now
        )
        val updatedOrder = orderRepository.update(cancelledOrder)

        val cancelledEvent = OrderCancelledEvent(
            orderId = updatedOrder.id,
            buyerId = updatedOrder.buyerId,
            reason = reason,
            cancelledAt = now
        )
        eventPublisher.publish(cancelledEvent)

        if (oldStatus == OrderStatus.AwaitingValidation || oldStatus == OrderStatus.StockConfirmed) {
            val stockReturnedEvent = StockReturnedEvent(
                orderId = updatedOrder.id,
                items = updatedOrder.items.map { StockValidationItem(it.menuItemId, it.quantity) }
            )
            eventPublisher.publish(stockReturnedEvent)
        }

        val statusChangedEvent = OrderStatusChangedEvent(
            orderId = updatedOrder.id,
            buyerId = updatedOrder.buyerId,
            oldStatus = oldStatus,
            newStatus = OrderStatus.Cancelled,
            totalPrice = updatedOrder.totalPrice,
            currency = updatedOrder.currency,
            description = reason,
            changedAt = now
        )
        eventPublisher.publish(statusChangedEvent)

        return updatedOrder
    }
}

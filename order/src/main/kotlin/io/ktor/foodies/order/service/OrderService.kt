package io.ktor.foodies.order.service

import io.ktor.foodies.order.OrderForbiddenException
import io.ktor.foodies.order.OrderNotFoundException
import io.ktor.foodies.order.client.BasketClient
import io.ktor.foodies.order.domain.*
import io.ktor.foodies.order.repository.OrderRepository
import io.ktor.foodies.server.validate
import java.math.BigDecimal
import kotlin.time.Instant

interface OrderService {
    suspend fun createOrder(
        requestId: String,
        buyerId: String,
        buyerEmail: String,
        buyerName: String,
        request: CreateOrderRequest,
        token: String
    ): Order

    suspend fun getOrders(
        buyerId: String,
        offset: Long,
        limit: Int,
        status: OrderStatus? = null
    ): PaginatedOrders

    suspend fun getAllOrders(
        offset: Long,
        limit: Int,
        status: OrderStatus? = null,
        buyerId: String? = null
    ): PaginatedOrders

    suspend fun getOrder(id: Long, buyerId: String): Order
    suspend fun cancelOrder(id: Long, buyerId: String, reason: String): Order
    suspend fun transitionToAwaitingValidation(id: Long): Order
    suspend fun shipOrder(id: Long): Order
    suspend fun setStockConfirmed(id: Long): Order
    suspend fun cancelOrderDueToStockRejection(id: Long, reason: String): Order
    suspend fun setPaid(id: Long): Order
    suspend fun cancelOrderDueToPaymentFailure(id: Long, reason: String): Order
}

class DefaultOrderService(
    private val orderRepository: OrderRepository,
    private val basketClient: BasketClient,
    private val eventPublisher: OrderEventPublisher,
) : OrderService {
    private var gracePeriodService: GracePeriodService? = null

    fun setGracePeriodService(service: GracePeriodService) {
        this.gracePeriodService = service
    }
    override suspend fun createOrder(
        requestId: String,
        buyerId: String,
        buyerEmail: String,
        buyerName: String,
        request: CreateOrderRequest,
        token: String
    ): Order {
        val existingOrder = orderRepository.findByRequestId(requestId)
        if (existingOrder != null) {
            return existingOrder
        }

        val address = request.validate()
        val basket = basketClient.getBasket(buyerId, token) ?: throw IllegalArgumentException("Basket not found")
        if (basket.items.isEmpty()) throw IllegalArgumentException("Basket is empty")

        val createOrder = CreateOrder(
            requestId = requestId,
            buyerId = buyerId,
            buyerEmail = buyerEmail,
            buyerName = buyerName,
            deliveryAddress = address,
            items = basket.items.map {
                CreateOrderItem(
                    menuItemId = it.menuItemId,
                    menuItemName = it.menuItemName,
                    pictureUrl = it.menuItemImageUrl,
                    unitPrice = it.unitPrice,
                    quantity = it.quantity,
                )
            },
            totalPrice = basket.items.sumOf { it.unitPrice * it.quantity.toBigDecimal() },
            paymentDetails = request.paymentDetails
        )

        val order = orderRepository.create(createOrder)

        val event = OrderCreatedEvent(
            orderId = order.id,
            buyerId = order.buyerId,
            items = order.items.map { OrderItemSnapshot(it.menuItemId, it.quantity, it.unitPrice) },
            totalPrice = order.totalPrice,
            createdAt = order.createdAt
        )
        eventPublisher.publish(event)

        gracePeriodService?.scheduleGracePeriodExpiration(order.id)

        return order
    }

    override suspend fun getOrders(
        buyerId: String,
        offset: Long,
        limit: Int,
        status: OrderStatus?
    ): PaginatedOrders = orderRepository.findByBuyerId(buyerId, offset, limit, status)

    override suspend fun getAllOrders(
        offset: Long,
        limit: Int,
        status: OrderStatus?,
        buyerId: String?
    ): PaginatedOrders = orderRepository.findAll(offset, limit, status, buyerId)

    override suspend fun getOrder(id: Long, buyerId: String): Order {
        val order = orderRepository.findById(id) ?: throw OrderNotFoundException(id)
        if (order.buyerId != buyerId) {
            throw OrderForbiddenException(id)
        }
        return order
    }

    override suspend fun cancelOrder(id: Long, buyerId: String, reason: String): Order {
        val order = getOrder(id, buyerId)
        if (order.status == OrderStatus.Cancelled) return order

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

        val statusChangedEvent = OrderStatusChangedEvent(
            orderId = updatedOrder.id,
            buyerId = updatedOrder.buyerId,
            oldStatus = oldStatus,
            newStatus = OrderStatus.Cancelled,
            description = reason,
            changedAt = now
        )
        eventPublisher.publish(statusChangedEvent)

        return updatedOrder
    }

    override suspend fun transitionToAwaitingValidation(id: Long): Order {
        val order = orderRepository.findById(id) ?: throw OrderNotFoundException(id)
        if (order.status != OrderStatus.Submitted) {
            return order // Already transitioned or cancelled
        }

        val oldStatus = order.status
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val updatedOrder = orderRepository.update(order.copy(status = OrderStatus.AwaitingValidation, updatedAt = now))

        val awaitingValidationEvent = OrderAwaitingValidationEvent(
            orderId = updatedOrder.id,
            buyerId = updatedOrder.buyerId,
            items = updatedOrder.items.map { StockValidationItem(it.menuItemId, it.quantity) }
        )
        eventPublisher.publish(awaitingValidationEvent)

        val statusChangedEvent = OrderStatusChangedEvent(
            orderId = updatedOrder.id,
            buyerId = updatedOrder.buyerId,
            oldStatus = oldStatus,
            newStatus = OrderStatus.AwaitingValidation,
            description = "Order moved to AwaitingValidation after grace period",
            changedAt = now
        )
        eventPublisher.publish(statusChangedEvent)

        return updatedOrder
    }

    override suspend fun shipOrder(id: Long): Order {
        val order = orderRepository.findById(id) ?: throw OrderNotFoundException(id)
        if (order.status != OrderStatus.Paid) {
            throw IllegalArgumentException("Order must be Paid to be shipped. Current status: ${order.status}")
        }

        val oldStatus = order.status
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val shippedOrder = order.copy(
            status = OrderStatus.Shipped,
            description = "Order shipped",
            updatedAt = now
        )
        val updatedOrder = orderRepository.update(shippedOrder)

        val event = OrderStatusChangedEvent(
            orderId = updatedOrder.id,
            buyerId = updatedOrder.buyerId,
            oldStatus = oldStatus,
            newStatus = OrderStatus.Shipped,
            description = "Order shipped",
            changedAt = now
        )
        eventPublisher.publish(event)
        return updatedOrder
    }
    
    override suspend fun setStockConfirmed(id: Long): Order {
        val order = orderRepository.findById(id) ?: throw OrderNotFoundException(id)
        if (order.status != OrderStatus.AwaitingValidation) {
            throw IllegalArgumentException("Order must be AwaitingValidation to be stock confirmed. Current status: ${order.status}")
        }

        val oldStatus = order.status
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val updatedOrder = orderRepository.update(order.copy(status = OrderStatus.StockConfirmed, updatedAt = now))

        val event = OrderStatusChangedEvent(
            orderId = updatedOrder.id,
            buyerId = updatedOrder.buyerId,
            oldStatus = oldStatus,
            newStatus = OrderStatus.StockConfirmed,
            description = "Stock confirmed by menu service",
            changedAt = now
        )
        eventPublisher.publish(event)
        return updatedOrder
    }

    override suspend fun cancelOrderDueToStockRejection(id: Long, reason: String): Order {
        val order = orderRepository.findById(id) ?: throw OrderNotFoundException(id)
        if (order.status != OrderStatus.AwaitingValidation) {
             throw IllegalArgumentException("Order must be AwaitingValidation to be cancelled due to stock rejection. Current status: ${order.status}")
        }

        val oldStatus = order.status
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val updatedOrder = orderRepository.update(order.copy(status = OrderStatus.Cancelled, description = reason, updatedAt = now))

        val cancelledEvent = OrderCancelledEvent(
            orderId = updatedOrder.id,
            buyerId = updatedOrder.buyerId,
            reason = reason,
            cancelledAt = now
        )
        eventPublisher.publish(cancelledEvent)

        val statusChangedEvent = OrderStatusChangedEvent(
            orderId = updatedOrder.id,
            buyerId = updatedOrder.buyerId,
            oldStatus = oldStatus,
            newStatus = OrderStatus.Cancelled,
            description = reason,
            changedAt = now
        )
        eventPublisher.publish(statusChangedEvent)

        return updatedOrder
    }

    override suspend fun setPaid(id: Long): Order {
        val order = orderRepository.findById(id) ?: throw OrderNotFoundException(id)
        if (order.status != OrderStatus.StockConfirmed) {
            throw IllegalArgumentException("Order must be StockConfirmed to be paid. Current status: ${order.status}")
        }

        val oldStatus = order.status
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val updatedOrder = orderRepository.update(order.copy(status = OrderStatus.Paid, updatedAt = now))

        val event = OrderStatusChangedEvent(
            orderId = updatedOrder.id,
            buyerId = updatedOrder.buyerId,
            oldStatus = oldStatus,
            newStatus = OrderStatus.Paid,
            description = "Payment succeeded",
            changedAt = now
        )
        eventPublisher.publish(event)
        return updatedOrder
    }

    override suspend fun cancelOrderDueToPaymentFailure(id: Long, reason: String): Order {
        val order = orderRepository.findById(id) ?: throw OrderNotFoundException(id)
        if (order.status != OrderStatus.StockConfirmed) {
            throw IllegalArgumentException("Order must be StockConfirmed to be cancelled due to payment failure. Current status: ${order.status}")
        }

        val oldStatus = order.status
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val updatedOrder = orderRepository.update(order.copy(status = OrderStatus.Cancelled, description = reason, updatedAt = now))

        val cancelledEvent = OrderCancelledEvent(
            orderId = updatedOrder.id,
            buyerId = updatedOrder.buyerId,
            reason = reason,
            cancelledAt = now
        )
        eventPublisher.publish(cancelledEvent)

        val statusChangedEvent = OrderStatusChangedEvent(
            orderId = updatedOrder.id,
            buyerId = updatedOrder.buyerId,
            oldStatus = oldStatus,
            newStatus = OrderStatus.Cancelled,
            description = reason,
            changedAt = now
        )
        eventPublisher.publish(statusChangedEvent)

        return updatedOrder
    }

    private fun CreateOrderRequest.validate(): Address = validate {
        Address(
            street = street.validate({ it.isNotBlank() }) { "street is required" },
            city = city.validate({ it.isNotBlank() }) { "city is required" },
            state = state.validate({ it.isNotBlank() }) { "state is required" },
            country = country.validate({ it.isNotBlank() }) { "country is required" },
            zipCode = zipCode.validate({ it.isNotBlank() }) { "zipCode is required" }
        )
    }
}

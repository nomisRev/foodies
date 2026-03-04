package io.ktor.foodies.order.fulfillment

import io.ktor.foodies.events.common.PaymentFailureCode
import io.ktor.foodies.events.common.PaymentMethodInfo
import io.ktor.foodies.events.common.PaymentMethodType
import io.ktor.foodies.events.menu.RejectedItem
import io.ktor.foodies.events.order.*
import io.ktor.foodies.order.Order
import io.ktor.foodies.order.PaymentMethod
import io.ktor.foodies.order.persistence.OrderRepository
import java.util.UUID
import kotlin.time.Instant

interface FulfillmentService {
    suspend fun transitionToAwaitingValidation(id: Long): Order?
    suspend fun setStockConfirmed(id: Long): Order?
    suspend fun processStockRejection(id: Long, rejectedItems: List<RejectedItem>): Order?
    suspend fun setPaid(id: Long): Order?
    suspend fun cancelOrderDueToPaymentFailure(id: Long, reason: String, code: PaymentFailureCode): Order?
    suspend fun shipOrder(requestId: UUID, id: Long): Order?
}

class DefaultFulfillmentService(
    private val fulfillmentRepository: OrderRepository,
    private val eventPublisher: FulfillmentEventPublisher,
) : FulfillmentService {

    override suspend fun transitionToAwaitingValidation(id: Long): Order? =
        fulfillmentRepository.findById(id)?.let { order ->
            if (order.status != OrderStatus.Submitted) {
                return@let order
            }

            val oldStatus = order.status
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            fulfillmentRepository.update(order.copy(status = OrderStatus.AwaitingValidation, updatedAt = now)).also { updatedOrder ->
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
                    totalPrice = updatedOrder.totalPrice,
                    currency = updatedOrder.currency,
                    description = "Order moved to AwaitingValidation after grace period",
                    changedAt = now
                )
                eventPublisher.publish(statusChangedEvent)
            }
        }

    override suspend fun shipOrder(requestId: UUID, id: Long): Order? =
        fulfillmentRepository.findById(id)?.let { order ->
            if (order.status == OrderStatus.Shipped) return@let order
            if (order.status != OrderStatus.Paid) {
                throw IllegalArgumentException("Order must be Paid to be shipped. Current status: ${order.status}")
            }

            val oldStatus = order.status
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            fulfillmentRepository.update(
                order.copy(
                    status = OrderStatus.Shipped,
                    description = "Order shipped",
                    updatedAt = now
                )
            ).also { updatedOrder ->
                val event = OrderStatusChangedEvent(
                    orderId = updatedOrder.id,
                    buyerId = updatedOrder.buyerId,
                    oldStatus = oldStatus,
                    newStatus = OrderStatus.Shipped,
                    totalPrice = updatedOrder.totalPrice,
                    currency = updatedOrder.currency,
                    description = "Order shipped",
                    changedAt = now
                )
                eventPublisher.publish(event)
            }
        }

    override suspend fun setStockConfirmed(id: Long): Order? =
        fulfillmentRepository.findById(id)?.let { order ->
            if (order.status != OrderStatus.AwaitingValidation) {
                throw IllegalArgumentException("Order must be AwaitingValidation to be stock confirmed. Current status: ${order.status}")
            }

            val oldStatus = order.status
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            fulfillmentRepository.update(order.copy(status = OrderStatus.StockConfirmed, updatedAt = now)).also { updatedOrder ->
                val event = OrderStatusChangedEvent(
                    orderId = updatedOrder.id,
                    buyerId = updatedOrder.buyerId,
                    oldStatus = oldStatus,
                    newStatus = OrderStatus.StockConfirmed,
                    totalPrice = updatedOrder.totalPrice,
                    currency = updatedOrder.currency,
                    description = "Stock confirmed by menu service",
                    changedAt = now
                )
                eventPublisher.publish(event)

                val stockConfirmedEvent = OrderStockConfirmedEvent(
                    eventId = UUID.randomUUID().toString(),
                    orderId = updatedOrder.id,
                    buyerId = updatedOrder.buyerId,
                    totalAmount = updatedOrder.totalPrice,
                    currency = updatedOrder.currency,
                    paymentMethod = updatedOrder.paymentMethod?.toPaymentMethodInfo()
                        ?: throw IllegalStateException("Payment method missing for order ${updatedOrder.id}"),
                    occurredAt = now
                )
                eventPublisher.publish(stockConfirmedEvent)
            }
        }

    override suspend fun processStockRejection(id: Long, rejectedItems: List<RejectedItem>): Order? =
        fulfillmentRepository.findById(id)?.let { order ->
            if (order.status != OrderStatus.AwaitingValidation) {
                throw IllegalArgumentException("Order must be AwaitingValidation to be processed for stock rejection. Current status: ${order.status}")
            }

            val oldStatus = order.status
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())

            val updatedItems = order.items.mapNotNull { item ->
                val rejection = rejectedItems.find { it.menuItemId == item.menuItemId }
                if (rejection != null) {
                    if (rejection.availableQuantity > 0) {
                        item.copy(quantity = rejection.availableQuantity)
                    } else {
                        null
                    }
                } else {
                    item
                }
            }

            if (updatedItems.isEmpty()) {
                val reason = "Stock rejected for items: " + rejectedItems.joinToString(", ") {
                    "${it.menuItemName} (Requested: ${it.requestedQuantity}, Available: ${it.availableQuantity})"
                }
                fulfillmentRepository.update(
                    order.copy(
                        status = OrderStatus.Cancelled,
                        description = reason,
                        updatedAt = now
                    )
                ).also { updatedOrder ->
                    eventPublisher.publish(
                        OrderCancelledEvent(
                            orderId = updatedOrder.id,
                            buyerId = updatedOrder.buyerId,
                            reason = reason,
                            cancelledAt = now
                        )
                    )

                    eventPublisher.publish(
                        OrderStatusChangedEvent(
                            orderId = updatedOrder.id,
                            buyerId = updatedOrder.buyerId,
                            oldStatus = oldStatus,
                            newStatus = OrderStatus.Cancelled,
                            totalPrice = updatedOrder.totalPrice,
                            currency = updatedOrder.currency,
                            description = reason,
                            changedAt = now
                        )
                    )
                }
            } else {
                val newTotalPrice = updatedItems.sumOf { it.unitPrice * it.quantity.toBigDecimal() }
                val description = if (updatedItems.size < order.items.size || updatedItems.any {
                        it.quantity < (order.items.find { old -> old.menuItemId == it.menuItemId }?.quantity ?: it.quantity)
                    }) {
                    "Order partially fulfilled due to stock availability"
                } else {
                    "Stock confirmed"
                }

                fulfillmentRepository.update(
                    order.copy(
                        status = OrderStatus.StockConfirmed,
                        items = updatedItems,
                        totalPrice = newTotalPrice,
                        description = description,
                        updatedAt = now
                    )
                ).also { updatedOrder ->
                    eventPublisher.publish(
                        OrderStatusChangedEvent(
                            orderId = updatedOrder.id,
                            buyerId = updatedOrder.buyerId,
                            oldStatus = oldStatus,
                            newStatus = OrderStatus.StockConfirmed,
                            totalPrice = updatedOrder.totalPrice,
                            currency = updatedOrder.currency,
                            description = description,
                            changedAt = now
                        )
                    )

                    val stockConfirmedEvent = OrderStockConfirmedEvent(
                        eventId = UUID.randomUUID().toString(),
                        orderId = updatedOrder.id,
                        buyerId = updatedOrder.buyerId,
                        totalAmount = updatedOrder.totalPrice,
                        currency = updatedOrder.currency,
                        paymentMethod = updatedOrder.paymentMethod?.toPaymentMethodInfo()
                            ?: throw IllegalStateException("Payment method missing for order ${updatedOrder.id}"),
                        occurredAt = now
                    )
                    eventPublisher.publish(stockConfirmedEvent)
                }
            }
        }

    override suspend fun setPaid(id: Long): Order? =
        fulfillmentRepository.findById(id)?.let { order ->
            if (order.status != OrderStatus.StockConfirmed) {
                throw IllegalArgumentException("Order must be StockConfirmed to be paid. Current status: ${order.status}")
            }

            val oldStatus = order.status
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            fulfillmentRepository.update(order.copy(status = OrderStatus.Paid, updatedAt = now)).also { updatedOrder ->
                val event = OrderStatusChangedEvent(
                    orderId = updatedOrder.id,
                    buyerId = updatedOrder.buyerId,
                    oldStatus = oldStatus,
                    newStatus = OrderStatus.Paid,
                    totalPrice = updatedOrder.totalPrice,
                    currency = updatedOrder.currency,
                    description = "Payment succeeded",
                    changedAt = now
                )
                eventPublisher.publish(event)
            }
        }

    override suspend fun cancelOrderDueToPaymentFailure(id: Long, reason: String, code: PaymentFailureCode): Order? =
        fulfillmentRepository.findById(id)?.let { order ->
            if (order.status != OrderStatus.StockConfirmed) {
                throw IllegalArgumentException("Order must be StockConfirmed to be cancelled due to payment failure. Current status: ${order.status}")
            }

            val oldStatus = order.status
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            val fullReason = "Payment failed ($code): $reason"
            fulfillmentRepository.update(
                order.copy(
                    status = OrderStatus.Cancelled,
                    description = fullReason,
                    updatedAt = now
                )
            ).also { updatedOrder ->
                val cancelledEvent = OrderCancelledEvent(
                    orderId = updatedOrder.id,
                    buyerId = updatedOrder.buyerId,
                    reason = fullReason,
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
                    description = fullReason,
                    changedAt = now
                )
                eventPublisher.publish(statusChangedEvent)
            }
        }

    private fun PaymentMethod.toPaymentMethodInfo(): PaymentMethodInfo {
        val type = PaymentMethodType.CREDIT_CARD
        return PaymentMethodInfo(
            type = type,
            cardLastFour = this.cardNumber,
            cardBrand = this.cardType,
            cardHolderName = this.cardHolderName,
            expirationMonth = this.expirationMonth,
            expirationYear = this.expirationYear
        )
    }
}

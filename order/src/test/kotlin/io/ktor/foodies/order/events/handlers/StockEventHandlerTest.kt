package io.ktor.foodies.order.events.handlers

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.order.createTestContext
import io.ktor.foodies.order.domain.*
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.time.Instant

val stockEventHandlerSpec by testSuite {
    test("StockConfirmedEventHandler should update order status to StockConfirmed") {
        val ctx = createTestContext()
        val order = createOrder(OrderStatus.AwaitingValidation)
        ctx.orderRepository.orders.add(order)
        val confirmedHandler = StockConfirmedEventHandler(ctx.service)

        confirmedHandler.handle(StockConfirmedEvent(order.id, Instant.fromEpochMilliseconds(0)))

        val updatedOrder = ctx.orderRepository.findById(order.id)
        assertEquals(OrderStatus.StockConfirmed, updatedOrder?.status)

        val statusEvent = ctx.eventPublisher.statusChangedEvents.last()
        assertEquals(OrderStatus.StockConfirmed, statusEvent.newStatus)
    }

    test("StockRejectedEventHandler should cancel order when all items rejected") {
        val ctx = createTestContext()
        val order = createOrder(OrderStatus.AwaitingValidation)
        ctx.orderRepository.orders.add(order)
        val rejectedHandler = StockRejectedEventHandler(ctx.service)

        rejectedHandler.handle(
            StockRejectedEvent(
                orderId = order.id,
                rejectedItems = listOf(RejectedItem(1, "Burger", 2, 0)),
                rejectedAt = Instant.fromEpochMilliseconds(0)
            )
        )

        val updatedOrder = ctx.orderRepository.findById(order.id)
        assertEquals(OrderStatus.Cancelled, updatedOrder?.status)
        assert(updatedOrder?.description?.contains("Stock rejected") == true)

        val cancelEvent = ctx.eventPublisher.cancelledEvents.last()
        assertEquals(order.id, cancelEvent.orderId)
    }

    test("StockRejectedEventHandler should partially fulfill order when some items available") {
        val ctx = createTestContext()
        val order = createOrder(OrderStatus.AwaitingValidation)
        ctx.orderRepository.orders.add(order)
        val rejectedHandler = StockRejectedEventHandler(ctx.service)

        rejectedHandler.handle(
            StockRejectedEvent(
                orderId = order.id,
                rejectedItems = listOf(RejectedItem(1, "Burger", 2, 1)),
                rejectedAt = Instant.fromEpochMilliseconds(0)
            )
        )

        val updatedOrder = ctx.orderRepository.findById(order.id)
        assertEquals(OrderStatus.StockConfirmed, updatedOrder?.status)
        assertEquals(1, updatedOrder?.items?.size)
        assertEquals(1, updatedOrder?.items?.first()?.quantity)
        assertEquals(BigDecimal.TEN.setScale(2), updatedOrder?.totalPrice?.setScale(2))
        assertEquals("Order partially fulfilled due to stock availability", updatedOrder?.description)

        val statusEvent = ctx.eventPublisher.statusChangedEvents.last()
        assertEquals(OrderStatus.StockConfirmed, statusEvent.newStatus)
    }
}

private fun createOrder(status: OrderStatus) = Order(
    id = 1,
    requestId = "req-1",
    buyerId = "user-1",
    buyerEmail = "user@test.com",
    buyerName = "User",
    status = status,
    deliveryAddress = Address("S", "C", "S", "C", "Z"),
    items = listOf(OrderItem(1, 1, "Burger", "url", BigDecimal.TEN, 2, BigDecimal.ZERO)),
    paymentMethod = PaymentMethod(1, CardType.Visa, "User", "1234", 12, 2030),
    totalPrice = BigDecimal.valueOf(20),
    currency = "USD",
    description = null,
    history = emptyList(),
    createdAt = Instant.fromEpochMilliseconds(0),
    updatedAt = Instant.fromEpochMilliseconds(0)
)

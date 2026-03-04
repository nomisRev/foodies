package io.ktor.foodies.order.fulfillment

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.events.common.CardBrand
import io.ktor.foodies.events.common.PaymentFailureCode
import io.ktor.foodies.events.menu.StockConfirmedEvent
import io.ktor.foodies.events.order.OrderStatus
import io.ktor.foodies.events.payment.OrderPaymentFailedEvent
import io.ktor.foodies.events.payment.OrderPaymentSucceededEvent
import io.ktor.foodies.order.*
import io.ktor.foodies.order.fulfillment.handlers.PaymentFailedHandler
import io.ktor.foodies.order.fulfillment.handlers.PaymentSucceededHandler
import io.ktor.foodies.order.fulfillment.handlers.StockConfirmedHandler
import java.math.BigDecimal
import java.util.*
import kotlin.test.assertEquals
import kotlin.time.Instant

val fulfillmentPaymentSpec by testSuite {
    test("Full flow: Order Submission -> Stock Confirmation -> Payment -> Paid") {
        val ctx = createTestContext()

        val order = createTestOrder(OrderStatus.Submitted)
        ctx.orderRepository.orders.add(order)

        ctx.fulfillmentService.transitionToAwaitingValidation(order.id)
        assertEquals(OrderStatus.AwaitingValidation, ctx.orderRepository.findById(order.id)?.status)

        val stockConfirmedHandler = StockConfirmedHandler(ctx.fulfillmentService)
        stockConfirmedHandler.handle(StockConfirmedEvent(order.id, Instant.fromEpochMilliseconds(0)))

        val stockConfirmedOrder = ctx.orderRepository.findById(order.id)
        assertEquals(OrderStatus.StockConfirmed, stockConfirmedOrder?.status)

        val publishedStockConfirmedEvent = ctx.fulfillmentEventPublisher.stockConfirmedEvents.last()
        assertEquals(order.id, publishedStockConfirmedEvent.orderId)
        assertEquals(order.buyerId, publishedStockConfirmedEvent.buyerId)
        assertEquals(order.totalPrice, publishedStockConfirmedEvent.totalAmount)
        assertEquals(order.currency, publishedStockConfirmedEvent.currency)

        val paymentSucceededEvent = OrderPaymentSucceededEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = publishedStockConfirmedEvent.orderId,
            paymentId = 100L,
            transactionId = "trans-12345",
            amount = publishedStockConfirmedEvent.totalAmount,
            currency = publishedStockConfirmedEvent.currency,
            processedAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        )

        val paymentSucceededHandler = PaymentSucceededHandler(ctx.fulfillmentService)
        paymentSucceededHandler.handle(paymentSucceededEvent)

        val finalOrder = ctx.orderRepository.findById(order.id)
        assertEquals(OrderStatus.Paid, finalOrder?.status)
    }

    test("Full flow: Order Submission -> Stock Confirmation -> Payment Failure -> Cancelled") {
        val ctx = createTestContext()

        val order = createTestOrder(OrderStatus.Submitted)
        ctx.orderRepository.orders.add(order)

        ctx.fulfillmentService.transitionToAwaitingValidation(order.id)

        val stockConfirmedHandler = StockConfirmedHandler(ctx.fulfillmentService)
        stockConfirmedHandler.handle(StockConfirmedEvent(order.id, Instant.fromEpochMilliseconds(0)))

        val publishedStockConfirmedEvent = ctx.fulfillmentEventPublisher.stockConfirmedEvents.last()

        val paymentFailedEvent = OrderPaymentFailedEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = publishedStockConfirmedEvent.orderId,
            failureReason = "Insufficient funds",
            failureCode = PaymentFailureCode.INSUFFICIENT_FUNDS,
            occurredAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        )

        val paymentFailedHandler = PaymentFailedHandler(ctx.fulfillmentService)
        paymentFailedHandler.handle(paymentFailedEvent)

        val finalOrder = ctx.orderRepository.findById(order.id)
        assertEquals(OrderStatus.Cancelled, finalOrder?.status)
        assert(finalOrder?.description?.contains("Insufficient funds") == true)

        val stockReturnedEvent = ctx.fulfillmentEventPublisher.stockReturnedEvents.last()
        assertEquals(order.id, stockReturnedEvent.orderId)
    }
}

private fun createTestOrder(status: OrderStatus) = Order(
    id = 1,
    requestId = "req-1",
    buyerId = "user-1",
    buyerEmail = "user@test.com",
    buyerName = "User",
    status = status,
    deliveryAddress = Address("Street", "City", "State", "Country", "12345"),
    items = listOf(OrderItem(1, 1, "Burger", "url", BigDecimal("10.00"), 2, BigDecimal.ZERO)),
    paymentMethod = PaymentMethod(1, CardBrand.VISA, "User", "1234", 12, 2030),
    totalPrice = BigDecimal("20.00"),
    currency = "USD",
    description = null,
    history = emptyList(),
    createdAt = Instant.fromEpochMilliseconds(0),
    updatedAt = Instant.fromEpochMilliseconds(0)
)

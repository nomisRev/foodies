package io.ktor.foodies.order

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.events.common.CardBrand
import io.ktor.foodies.events.common.PaymentFailureCode
import io.ktor.foodies.events.menu.StockConfirmedEvent
import io.ktor.foodies.events.payment.OrderPaymentFailedEvent
import io.ktor.foodies.events.payment.OrderPaymentSucceededEvent
import io.ktor.foodies.order.domain.*
import io.ktor.foodies.events.order.*
import io.ktor.foodies.order.events.handlers.PaymentFailedEventHandler
import io.ktor.foodies.order.events.handlers.PaymentSucceededEventHandler
import io.ktor.foodies.order.events.handlers.StockConfirmedEventHandler
import java.math.BigDecimal
import java.util.*
import kotlin.test.assertEquals
import kotlin.time.Instant

val orderPaymentIntegrationSpec by testSuite {
    test("Full flow: Order Submission -> Stock Confirmation -> Payment -> Paid") {
        val ctx = createTestContext()
        val orderService = ctx.service
        
        // 1. Creates an order
        val order = createTestOrder(OrderStatus.Submitted)
        ctx.orderRepository.orders.add(order)
        
        // Transition to AwaitingValidation (simulating grace period expiration)
        orderService.transitionToAwaitingValidation(order.id)
        assertEquals(OrderStatus.AwaitingValidation, ctx.orderRepository.findById(order.id)?.status)
        
        // 2. Confirms stock
        // This is usually triggered by StockConfirmedEvent from Menu service
        val stockConfirmedHandler = StockConfirmedEventHandler(orderService)
        stockConfirmedHandler.handle(StockConfirmedEvent(order.id, Instant.fromEpochMilliseconds(0)))
        
        // 3. Verifies OrderStockConfirmedEvent is published
        val stockConfirmedOrder = ctx.orderRepository.findById(order.id)
        assertEquals(OrderStatus.StockConfirmed, stockConfirmedOrder?.status)
        
        val publishedStockConfirmedEvent = ctx.eventPublisher.stockConfirmedEvents.last()
        assertEquals(order.id, publishedStockConfirmedEvent.orderId)
        assertEquals(order.buyerId, publishedStockConfirmedEvent.buyerId)
        assertEquals(order.totalPrice, publishedStockConfirmedEvent.totalAmount)
        assertEquals(order.currency, publishedStockConfirmedEvent.currency)
        
        // 4. Verifies Payment service processes payment
        // We simulate the Payment service processing and publishing OrderPaymentSucceededEvent
        val paymentSucceededEvent = OrderPaymentSucceededEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = publishedStockConfirmedEvent.orderId,
            paymentId = 100L,
            transactionId = "trans-12345",
            amount = publishedStockConfirmedEvent.totalAmount,
            currency = publishedStockConfirmedEvent.currency,
            processedAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        )
        
        // 5. Verifies Order status updates to Paid
        val paymentSucceededHandler = PaymentSucceededEventHandler(orderService)
        paymentSucceededHandler.handle(paymentSucceededEvent)
        
        val finalOrder = ctx.orderRepository.findById(order.id)
        assertEquals(OrderStatus.Paid, finalOrder?.status)
    }

    test("Full flow: Order Submission -> Stock Confirmation -> Payment Failure -> Cancelled") {
        val ctx = createTestContext()
        val orderService = ctx.service
        
        // 1. Creates an order
        val order = createTestOrder(OrderStatus.Submitted)
        ctx.orderRepository.orders.add(order)
        
        // Transition to AwaitingValidation
        orderService.transitionToAwaitingValidation(order.id)
        
        // 2. Confirms stock
        val stockConfirmedHandler = StockConfirmedEventHandler(orderService)
        stockConfirmedHandler.handle(StockConfirmedEvent(order.id, Instant.fromEpochMilliseconds(0)))
        
        val publishedStockConfirmedEvent = ctx.eventPublisher.stockConfirmedEvents.last()
        
        // 4. Simulate Payment Failure
        val paymentFailedEvent = OrderPaymentFailedEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = publishedStockConfirmedEvent.orderId,
            failureReason = "Insufficient funds",
            failureCode = PaymentFailureCode.INSUFFICIENT_FUNDS,
            occurredAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        )
        
        // 5. Verifies Order status updates to Cancelled
        val paymentFailedHandler = PaymentFailedEventHandler(orderService)
        paymentFailedHandler.handle(paymentFailedEvent)
        
        val finalOrder = ctx.orderRepository.findById(order.id)
        assertEquals(OrderStatus.Cancelled, finalOrder?.status)
        assert(finalOrder?.description?.contains("Insufficient funds") == true)
        
        // Verify stock is returned
        val stockReturnedEvent = ctx.eventPublisher.stockReturnedEvents.last()
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

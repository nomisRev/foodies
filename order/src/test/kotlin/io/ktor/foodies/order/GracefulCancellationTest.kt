package io.ktor.foodies.order

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.events.common.CardBrand
import io.ktor.foodies.order.domain.*
import io.ktor.foodies.events.order.*
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val gracefulCancellationSpec by testSuite {
    test("cancelling order in StockConfirmed state should publish stock return information") {
        val ctx = createTestContext()

        // Setup: Create an order and move it to StockConfirmed
        val order = ctx.orderRepository.create(
            CreateOrder(
                requestId = "req-1",
                buyerId = "buyer-1",
                buyerEmail = "buyer@test.com",
                buyerName = "John",
                deliveryAddress = Address("Street", "City", "State", "Country", "12345"),
                items = listOf(
                    CreateOrderItem(1L, "Burger", "url", BigDecimal("10.00"), 2)
                ),
                totalPrice = BigDecimal("20.00"),
                currency = "USD",
                paymentDetails = PaymentDetails(CardBrand.VISA, "1234567812345678", "John", "123", 12, 2030)
            )
        )

        val stockConfirmedOrder = order.copy(status = OrderStatus.StockConfirmed)
        ctx.orderRepository.update(stockConfirmedOrder)

        // Action: Cancel the order
        ctx.service.cancelOrder(UUID.randomUUID(), order.id, "buyer-1", "User cancelled")

        // Verification: Check for stock return information
        assertTrue(ctx.eventPublisher.stockReturnedEvents.isNotEmpty(), "Expected StockReturnedEvent to be published")
        assertEquals(order.id, ctx.eventPublisher.stockReturnedEvents[0].orderId)
        assertEquals(1, ctx.eventPublisher.stockReturnedEvents[0].items.size)
        assertEquals(1L, ctx.eventPublisher.stockReturnedEvents[0].items[0].menuItemId)
        assertEquals(2, ctx.eventPublisher.stockReturnedEvents[0].items[0].quantity)
    }
}

package io.ktor.foodies.order.tracking

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.events.common.CardBrand
import io.ktor.foodies.events.order.OrderStatus
import io.ktor.foodies.order.*
import io.ktor.foodies.order.placement.CreateOrder
import io.ktor.foodies.order.placement.CreateOrderItem
import io.ktor.foodies.order.placement.PaymentDetails
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val trackingCancellationSpec by testSuite {
    test("cancelling order in StockConfirmed state should publish stock return information") {
        val ctx = createTestContext()

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

        ctx.trackingService.cancelOrder(UUID.randomUUID(), order.id, "buyer-1", "User cancelled")

        assertTrue(ctx.trackingEventPublisher.stockReturnedEvents.isNotEmpty(), "Expected StockReturnedEvent to be published")
        assertEquals(order.id, ctx.trackingEventPublisher.stockReturnedEvents[0].orderId)
        assertEquals(1, ctx.trackingEventPublisher.stockReturnedEvents[0].items.size)
        assertEquals(1L, ctx.trackingEventPublisher.stockReturnedEvents[0].items[0].menuItemId)
        assertEquals(2, ctx.trackingEventPublisher.stockReturnedEvents[0].items[0].quantity)
    }

    test("should cancel order and publish event") {
        val ctx = createTestContext()
        ctx.basketClient.basket = io.ktor.foodies.order.placement.CustomerBasket(
            buyerId = "buyer-1",
            items = listOf(
                io.ktor.foodies.order.placement.BasketItem(1, "Burger", "url", BigDecimal("10.00"), 2)
            )
        )

        val request = io.ktor.foodies.order.placement.CreateOrderRequest(
            street = "Street",
            city = "City",
            state = "State",
            country = "Country",
            zipCode = "12345",
            paymentDetails = PaymentDetails(
                cardType = CardBrand.VISA,
                cardNumber = "1234567812345678",
                cardHolderName = "John Doe",
                cardSecurityNumber = "123",
                expirationMonth = 12,
                expirationYear = 2030
            )
        )

        val order = ctx.placementService.createOrder(UUID.randomUUID(), "buyer-1", "buyer@test.com", "John", request, "token")

        val cancelledOrder = ctx.trackingService.cancelOrder(UUID.randomUUID(), order.id, "buyer-1", "Changed my mind")

        assertEquals(OrderStatus.Cancelled, cancelledOrder.status)
        assertEquals("Changed my mind", cancelledOrder.description)
        assertEquals(1, ctx.trackingEventPublisher.cancelledEvents.size)
        assertEquals(order.id, ctx.trackingEventPublisher.cancelledEvents[0].orderId)
        assertEquals("Changed my mind", ctx.trackingEventPublisher.cancelledEvents[0].reason)

        assertEquals(1, ctx.trackingEventPublisher.statusChangedEvents.size)
        assertEquals(OrderStatus.Submitted, ctx.trackingEventPublisher.statusChangedEvents[0].oldStatus)
        assertEquals(OrderStatus.Cancelled, ctx.trackingEventPublisher.statusChangedEvents[0].newStatus)
    }

    test("should return existing order when cancelling already cancelled order") {
        val ctx = createTestContext()
        ctx.basketClient.basket = io.ktor.foodies.order.placement.CustomerBasket(
            buyerId = "buyer-1",
            items = listOf(
                io.ktor.foodies.order.placement.BasketItem(1, "Burger", "url", BigDecimal("10.00"), 2)
            )
        )

        val request = io.ktor.foodies.order.placement.CreateOrderRequest(
            street = "Street",
            city = "City",
            state = "State",
            country = "Country",
            zipCode = "12345",
            paymentDetails = PaymentDetails(
                cardType = CardBrand.VISA,
                cardNumber = "1234567812345678",
                cardHolderName = "John Doe",
                cardSecurityNumber = "123",
                expirationMonth = 12,
                expirationYear = 2030
            )
        )

        val order = ctx.placementService.createOrder(UUID.randomUUID(), "buyer-1", "buyer@test.com", "John", request, "token")

        val cancelledOrder = order.copy(status = OrderStatus.Cancelled)
        ctx.orderRepository.update(cancelledOrder)

        val result = ctx.trackingService.cancelOrder(UUID.randomUUID(), order.id, "buyer-1", "Reason")

        assertEquals(OrderStatus.Cancelled, result.status)
        assertEquals(0, ctx.trackingEventPublisher.cancelledEvents.size)
        assertEquals(0, ctx.trackingEventPublisher.statusChangedEvents.size)
    }

    test("should throw error if cancelling paid order") {
        val ctx = createTestContext()
        ctx.basketClient.basket = io.ktor.foodies.order.placement.CustomerBasket(
            buyerId = "buyer-1",
            items = listOf(
                io.ktor.foodies.order.placement.BasketItem(1, "Burger", "url", BigDecimal("10.00"), 2)
            )
        )

        val request = io.ktor.foodies.order.placement.CreateOrderRequest(
            street = "Street",
            city = "City",
            state = "State",
            country = "Country",
            zipCode = "12345",
            paymentDetails = PaymentDetails(
                cardType = CardBrand.VISA,
                cardNumber = "1234567812345678",
                cardHolderName = "John Doe",
                cardSecurityNumber = "123",
                expirationMonth = 12,
                expirationYear = 2030
            )
        )

        val order = ctx.placementService.createOrder(UUID.randomUUID(), "buyer-1", "buyer@test.com", "John", request, "token")

        val paidOrder = order.copy(status = OrderStatus.Paid)
        ctx.orderRepository.update(paidOrder)

        kotlin.test.assertFailsWith<IllegalArgumentException> {
            ctx.trackingService.cancelOrder(UUID.randomUUID(), order.id, "buyer-1", "Reason")
        }
    }
}

package io.ktor.foodies.order.placement

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.events.common.CardBrand
import io.ktor.foodies.order.createTestContext
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals

val placementServiceSpec by testSuite {
    test("should create order and publish event") {
        val ctx = createTestContext()
        ctx.basketClient.basket = CustomerBasket(
            buyerId = "buyer-1",
            items = listOf(
                BasketItem(1, "Burger", "url", BigDecimal("10.00"), 2)
            )
        )

        val request = CreateOrderRequest(
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

        assertEquals(1, order.id)
        assertEquals("buyer-1", order.buyerId)
        assertEquals(BigDecimal("20.00"), order.totalPrice)
        assertEquals(1, ctx.placementEventPublisher.createdEvents.size)
        assertEquals(order.id, ctx.placementEventPublisher.createdEvents[0].orderId)
    }

    test("should return existing order for same request id") {
        val ctx = createTestContext()
        ctx.basketClient.basket = CustomerBasket(
            buyerId = "buyer-1",
            items = listOf(
                BasketItem(1, "Burger", "url", BigDecimal("10.00"), 2)
            )
        )

        val request = CreateOrderRequest(
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

        val requestId = UUID.randomUUID()
        val order1 = ctx.placementService.createOrder(requestId, "buyer-1", "buyer@test.com", "John", request, "token")
        val order2 = ctx.placementService.createOrder(requestId, "buyer-1", "buyer@test.com", "John", request, "token")

        assertEquals(order1.id, order2.id)
        assertEquals(1, ctx.orderRepository.orders.size)
        assertEquals(1, ctx.placementEventPublisher.createdEvents.size)
    }
}

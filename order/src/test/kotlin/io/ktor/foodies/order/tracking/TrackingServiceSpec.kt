package io.ktor.foodies.order.tracking

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.events.common.CardBrand
import io.ktor.foodies.events.order.OrderStatus
import io.ktor.foodies.order.*
import io.ktor.foodies.basket.routes.BasketItem
import io.ktor.foodies.basket.routes.CustomerBasket
import io.ktor.foodies.order.placement.CreateOrderRequest
import io.ktor.foodies.order.placement.PaymentDetails
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals

val trackingServiceSpec by testSuite {
    test("should return paginated orders") {
        val ctx = createTestContext()
        ctx.basketClient.basket = CustomerBasket(
            buyerId = "buyer-1",
            items = listOf(
                BasketItem("item-1", 1, "Burger", "", "url", BigDecimal("10.00"), 2)
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

        ctx.placementService.createOrder(UUID.randomUUID(), "buyer-1", "buyer@test.com", "John", request, "token")
        ctx.placementService.createOrder(UUID.randomUUID(), "buyer-1", "buyer@test.com", "John", request, "token")

        val paginated = ctx.trackingService.getOrders("buyer-1", 0, 10)

        assertEquals(2, paginated.orders.size)
        assertEquals(2, paginated.total)
    }

    test("should get order details") {
        val ctx = createTestContext()
        val existingOrder = Order(
            id = 123,
            requestId = "req-123",
            buyerId = "user-1",
            buyerEmail = "user1@test.com",
            buyerName = "User One",
            status = OrderStatus.Submitted,
            deliveryAddress = Address("Street", "City", "State", "Country", "12345"),
            items = emptyList(),
            paymentMethod = null,
            totalPrice = BigDecimal("10.00"),
            currency = "USD",
            description = null,
            history = emptyList(),
            createdAt = kotlin.time.Instant.fromEpochMilliseconds(0),
            updatedAt = kotlin.time.Instant.fromEpochMilliseconds(0)
        )
        ctx.orderRepository.orders.add(existingOrder)

        val result = ctx.trackingService.getOrder(123, "user-1")

        assert(result is GetOrderResult.Success)
        val order = (result as GetOrderResult.Success).order
        assertEquals(123, order.id)
        assertEquals("user-1", order.buyerId)
    }

    test("should return NotFound when order does not exist") {
        val ctx = createTestContext()
        val result = ctx.trackingService.getOrder(999, "user-1")
        assert(result is GetOrderResult.NotFound)
    }

    test("should return Forbidden when order belongs to different user") {
        val ctx = createTestContext()
        val existingOrder = Order(
            id = 456,
            requestId = "req-456",
            buyerId = "user-2",
            buyerEmail = "user2@test.com",
            buyerName = "User Two",
            status = OrderStatus.Submitted,
            deliveryAddress = Address("Street", "City", "State", "Country", "12345"),
            items = emptyList(),
            paymentMethod = null,
            totalPrice = BigDecimal("10.00"),
            currency = "USD",
            description = null,
            history = emptyList(),
            createdAt = kotlin.time.Instant.fromEpochMilliseconds(0),
            updatedAt = kotlin.time.Instant.fromEpochMilliseconds(0)
        )
        ctx.orderRepository.orders.add(existingOrder)

        val result = ctx.trackingService.getOrder(456, "user-1")
        assert(result is GetOrderResult.Forbidden)
    }
}

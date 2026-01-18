package io.ktor.foodies.order

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.order.domain.*
import io.ktor.foodies.events.order.OrderStatus
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.time.Instant

val getOrderDetailsSpec by testSuite {
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
            createdAt = Instant.fromEpochMilliseconds(0),
            updatedAt = Instant.fromEpochMilliseconds(0)
        )
        ctx.orderRepository.orders.add(existingOrder)

        val result = ctx.service.getOrder(123, "user-1")

        assert(result is GetOrderResult.Success)
        val order = (result as GetOrderResult.Success).order
        assertEquals(123, order.id)
        assertEquals("user-1", order.buyerId)
    }

    test("should return NotFound when order does not exist") {
        val ctx = createTestContext()
        val result = ctx.service.getOrder(999, "user-1")
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
            createdAt = Instant.fromEpochMilliseconds(0),
            updatedAt = Instant.fromEpochMilliseconds(0)
        )
        ctx.orderRepository.orders.add(existingOrder)

        val result = ctx.service.getOrder(456, "user-1")
        assert(result is GetOrderResult.Forbidden)
    }
}

package io.ktor.foodies.order.events.handlers

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.order.createTestContext
import io.ktor.foodies.order.domain.*
import io.ktor.foodies.events.order.*
import io.ktor.foodies.events.common.CardBrand
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.time.Instant

val gracePeriodExpiredEventHandlerSpec by testSuite {
    test("GracePeriodExpiredEventHandler should transition order to AwaitingValidation") {
        val ctx = createTestContext()
        val order = createOrder(OrderStatus.Submitted)
        ctx.orderRepository.orders.add(order)
        val handler = GracePeriodExpiredEventHandler(ctx.service)

        handler.handle(GracePeriodExpiredEvent(order.id, Instant.fromEpochMilliseconds(0)))

        val updatedOrder = ctx.orderRepository.findById(order.id)
        assertEquals(OrderStatus.AwaitingValidation, updatedOrder?.status)

        val statusEvent = ctx.eventPublisher.statusChangedEvents.last()
        assertEquals(OrderStatus.AwaitingValidation, statusEvent.newStatus)
        assertEquals("Order moved to AwaitingValidation after grace period", statusEvent.description)
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
    paymentMethod = PaymentMethod(1, CardBrand.VISA, "User", "1234", 12, 2030),
    totalPrice = BigDecimal.valueOf(20),
    currency = "USD",
    description = null,
    history = emptyList(),
    createdAt = Instant.fromEpochMilliseconds(0),
    updatedAt = Instant.fromEpochMilliseconds(0)
)

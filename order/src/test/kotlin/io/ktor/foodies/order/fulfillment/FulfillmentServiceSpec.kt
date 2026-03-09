package io.ktor.foodies.order.fulfillment

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.events.common.CardBrand
import io.ktor.foodies.events.common.PaymentFailureCode
import io.ktor.foodies.events.menu.RejectedItem
import io.ktor.foodies.events.order.OrderStatus
import io.ktor.foodies.order.*
import io.ktor.foodies.order.placement.BasketItem
import io.ktor.foodies.order.placement.CreateOrderRequest
import io.ktor.foodies.order.placement.CustomerBasket
import io.ktor.foodies.order.placement.PaymentDetails
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

val fulfillmentServiceSpec by testSuite {
    test("should transition to awaiting validation and publish events") {
        val ctx = createTestContext()
        ctx.basketClient.basket = CustomerBasket(
            buyerId = "buyer-1",
            items = listOf(BasketItem(1, "Burger", "url", BigDecimal("10.00"), 2))
        )

        val request = CreateOrderRequest(
            street = "Street", city = "City", state = "State", country = "Country", zipCode = "12345",
            paymentDetails = PaymentDetails(CardBrand.VISA, "1234567812345678", "John Doe", "123", 12, 2030)
        )

        val order = ctx.placementService.createOrder(UUID.randomUUID(), "buyer-1", "buyer@test.com", "John", request, "token")

        val updatedOrder = ctx.fulfillmentService.transitionToAwaitingValidation(order.id)

        assertNotNull(updatedOrder)
        assertEquals(OrderStatus.AwaitingValidation, updatedOrder.status)
        assertEquals(1, ctx.fulfillmentEventPublisher.awaitingValidationEvents.size)
        assertEquals(order.id, ctx.fulfillmentEventPublisher.awaitingValidationEvents[0].orderId)
        assertEquals(1, ctx.fulfillmentEventPublisher.awaitingValidationEvents[0].items.size)
        assertEquals(1L, ctx.fulfillmentEventPublisher.awaitingValidationEvents[0].items[0].menuItemId)
        assertEquals(2, ctx.fulfillmentEventPublisher.awaitingValidationEvents[0].items[0].quantity)

        val statusChangedEvent = ctx.fulfillmentEventPublisher.statusChangedEvents.last()
        assertEquals(OrderStatus.Submitted, statusChangedEvent.oldStatus)
        assertEquals(OrderStatus.AwaitingValidation, statusChangedEvent.newStatus)
    }

    test("should ship order and publish event") {
        val ctx = createTestContext()
        ctx.basketClient.basket = CustomerBasket(
            buyerId = "buyer-1",
            items = listOf(BasketItem(1, "Burger", "url", BigDecimal("10.00"), 2))
        )

        val request = CreateOrderRequest(
            street = "Street", city = "City", state = "State", country = "Country", zipCode = "12345",
            paymentDetails = PaymentDetails(CardBrand.VISA, "1234567812345678", "John Doe", "123", 12, 2030)
        )

        val order = ctx.placementService.createOrder(UUID.randomUUID(), "buyer-1", "buyer@test.com", "John", request, "token")

        val paidOrder = order.copy(status = OrderStatus.Paid)
        ctx.orderRepository.update(paidOrder)

        val shippedOrder = ctx.fulfillmentService.shipOrder(UUID.randomUUID(), order.id)

        assertNotNull(shippedOrder)
        assertEquals(OrderStatus.Shipped, shippedOrder.status)
        assertEquals(1, ctx.fulfillmentEventPublisher.statusChangedEvents.size)
        assertEquals(OrderStatus.Paid, ctx.fulfillmentEventPublisher.statusChangedEvents[0].oldStatus)
        assertEquals(OrderStatus.Shipped, ctx.fulfillmentEventPublisher.statusChangedEvents[0].newStatus)
    }

    test("should return existing order when shipping already shipped order") {
        val ctx = createTestContext()
        ctx.basketClient.basket = CustomerBasket(
            buyerId = "buyer-1",
            items = listOf(BasketItem(1, "Burger", "url", BigDecimal("10.00"), 2))
        )

        val request = CreateOrderRequest(
            street = "Street", city = "City", state = "State", country = "Country", zipCode = "12345",
            paymentDetails = PaymentDetails(CardBrand.VISA, "1234567812345678", "John Doe", "123", 12, 2030)
        )

        val order = ctx.placementService.createOrder(UUID.randomUUID(), "buyer-1", "buyer@test.com", "John", request, "token")

        val shippedOrder = order.copy(status = OrderStatus.Shipped)
        ctx.orderRepository.update(shippedOrder)

        val result = ctx.fulfillmentService.shipOrder(UUID.randomUUID(), order.id)

        assertNotNull(result)
        assertEquals(OrderStatus.Shipped, result.status)
        assertEquals(0, ctx.fulfillmentEventPublisher.statusChangedEvents.size)
    }

    test("should throw error if shipping order that is not paid") {
        val ctx = createTestContext()
        ctx.basketClient.basket = CustomerBasket(
            buyerId = "buyer-1",
            items = listOf(BasketItem(1, "Burger", "url", BigDecimal("10.00"), 2))
        )

        val request = CreateOrderRequest(
            street = "Street", city = "City", state = "State", country = "Country", zipCode = "12345",
            paymentDetails = PaymentDetails(CardBrand.VISA, "1234567812345678", "John Doe", "123", 12, 2030)
        )

        val order = ctx.placementService.createOrder(UUID.randomUUID(), "buyer-1", "buyer@test.com", "John", request, "token")

        assertFailsWith<IllegalArgumentException> {
            ctx.fulfillmentService.shipOrder(UUID.randomUUID(), order.id)
        }
    }

    test("should cancel order when stock rejection includes partial availability") {
        val ctx = createTestContext()
        ctx.basketClient.basket = CustomerBasket(
            buyerId = "buyer-1",
            items = listOf(BasketItem(1, "Burger", "url", BigDecimal("10.00"), 2))
        )

        val request = CreateOrderRequest(
            street = "Street", city = "City", state = "State", country = "Country", zipCode = "12345",
            paymentDetails = PaymentDetails(CardBrand.VISA, "1234567812345678", "John Doe", "123", 12, 2030)
        )

        val order = ctx.placementService.createOrder(UUID.randomUUID(), "buyer-1", "buyer@test.com", "John", request, "token")
        ctx.fulfillmentService.transitionToAwaitingValidation(order.id)
        val updatedOrder = ctx.fulfillmentService.processStockRejection(
            order.id,
            listOf(RejectedItem(1, "Burger", 2, 1))
        )

        assertNotNull(updatedOrder)
        assertEquals(OrderStatus.Cancelled, updatedOrder.status)
        assert(updatedOrder.description?.contains("Stock rejected") == true)
        assertEquals(1, ctx.fulfillmentEventPublisher.cancelledEvents.size)
        assertEquals(0, ctx.fulfillmentEventPublisher.stockConfirmedEvents.size)
    }

    test("should cancel order due to payment failure and publish events") {
        val ctx = createTestContext()
        ctx.basketClient.basket = CustomerBasket(
            buyerId = "buyer-1",
            items = listOf(BasketItem(1, "Burger", "url", BigDecimal("10.00"), 2))
        )

        val request = CreateOrderRequest(
            street = "Street", city = "City", state = "State", country = "Country", zipCode = "12345",
            paymentDetails = PaymentDetails(CardBrand.VISA, "1234567812345678", "John Doe", "123", 12, 2030)
        )

        val order = ctx.placementService.createOrder(UUID.randomUUID(), "buyer-1", "buyer@test.com", "John", request, "token")

        val stockConfirmedOrder = order.copy(status = OrderStatus.StockConfirmed)
        ctx.orderRepository.update(stockConfirmedOrder)

        val cancelledOrder = ctx.fulfillmentService.cancelOrderDueToPaymentFailure(order.id, "Insufficient funds", PaymentFailureCode.INSUFFICIENT_FUNDS)

        assertNotNull(cancelledOrder)
        assertEquals(OrderStatus.Cancelled, cancelledOrder.status)
        assertEquals("Payment failed (INSUFFICIENT_FUNDS): Insufficient funds", cancelledOrder.description)

        assertEquals(1, ctx.fulfillmentEventPublisher.cancelledEvents.size)
        assertEquals("Payment failed (INSUFFICIENT_FUNDS): Insufficient funds", ctx.fulfillmentEventPublisher.cancelledEvents[0].reason)

        assertEquals(1, ctx.fulfillmentEventPublisher.stockReturnedEvents.size)
        assertEquals(order.id, ctx.fulfillmentEventPublisher.stockReturnedEvents[0].orderId)
    }
}

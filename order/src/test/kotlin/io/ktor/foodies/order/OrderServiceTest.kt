package io.ktor.foodies.order

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.order.client.BasketItem
import io.ktor.foodies.order.client.CustomerBasket
import io.ktor.foodies.order.domain.*
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

val orderServiceSpec by testSuite {
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

        val order = ctx.service.createOrder(UUID.randomUUID(), "buyer-1", "buyer@test.com", "John", request, "token")

        assertEquals(1, order.id)
        assertEquals("buyer-1", order.buyerId)
        assertEquals(BigDecimal("20.00"), order.totalPrice)
        assertEquals(1, ctx.eventPublisher.createdEvents.size)
        assertEquals(order.id, ctx.eventPublisher.createdEvents[0].orderId)
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
        val order1 = ctx.service.createOrder(requestId, "buyer-1", "buyer@test.com", "John", request, "token")
        val order2 = ctx.service.createOrder(requestId, "buyer-1", "buyer@test.com", "John", request, "token")

        assertEquals(order1.id, order2.id)
        assertEquals(1, ctx.orderRepository.orders.size)
        assertEquals(1, ctx.eventPublisher.createdEvents.size)
    }

    test("should return paginated orders") {
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

        ctx.service.createOrder(UUID.randomUUID(), "buyer-1", "buyer@test.com", "John", request, "token")
        ctx.service.createOrder(UUID.randomUUID(), "buyer-1", "buyer@test.com", "John", request, "token")

        val paginated = ctx.service.getOrders("buyer-1", 0, 10)

        assertEquals(2, paginated.orders.size)
        assertEquals(2, paginated.total)
    }

    test("should cancel order and publish event") {
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

        val order = ctx.service.createOrder(UUID.randomUUID(), "buyer-1", "buyer@test.com", "John", request, "token")

        val cancelledOrder = ctx.service.cancelOrder(UUID.randomUUID(), order.id, "buyer-1", "Changed my mind")

        assertEquals(OrderStatus.Cancelled, cancelledOrder.status)
        assertEquals("Changed my mind", cancelledOrder.description)
        assertEquals(1, ctx.eventPublisher.cancelledEvents.size)
        assertEquals(order.id, ctx.eventPublisher.cancelledEvents[0].orderId)
        assertEquals("Changed my mind", ctx.eventPublisher.cancelledEvents[0].reason)

        assertEquals(1, ctx.eventPublisher.statusChangedEvents.size)
        assertEquals(OrderStatus.Submitted, ctx.eventPublisher.statusChangedEvents[0].oldStatus)
        assertEquals(OrderStatus.Cancelled, ctx.eventPublisher.statusChangedEvents[0].newStatus)
    }

    test("should return existing order when cancelling already cancelled order") {
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

        val order = ctx.service.createOrder(UUID.randomUUID(), "buyer-1", "buyer@test.com", "John", request, "token")

        // Manually set status to Cancelled
        val cancelledOrder = order.copy(status = OrderStatus.Cancelled)
        ctx.orderRepository.update(cancelledOrder)

        val result = ctx.service.cancelOrder(UUID.randomUUID(), order.id, "buyer-1", "Reason")

        assertEquals(OrderStatus.Cancelled, result.status)
        assertEquals(0, ctx.eventPublisher.cancelledEvents.size)
        assertEquals(0, ctx.eventPublisher.statusChangedEvents.size)
    }

    test("should ship order and publish event") {
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

        val order = ctx.service.createOrder(UUID.randomUUID(), "buyer-1", "buyer@test.com", "John", request, "token")

        // Manually set status to Paid
        val paidOrder = order.copy(status = OrderStatus.Paid)
        ctx.orderRepository.update(paidOrder)

        val shippedOrder = ctx.service.shipOrder(UUID.randomUUID(), order.id)

        assertNotNull(shippedOrder)
        assertEquals(OrderStatus.Shipped, shippedOrder.status)
        assertEquals(1, ctx.eventPublisher.statusChangedEvents.size)
        assertEquals(OrderStatus.Paid, ctx.eventPublisher.statusChangedEvents[0].oldStatus)
        assertEquals(OrderStatus.Shipped, ctx.eventPublisher.statusChangedEvents[0].newStatus)
    }

    test("should return existing order when shipping already shipped order") {
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

        val order = ctx.service.createOrder(UUID.randomUUID(), "buyer-1", "buyer@test.com", "John", request, "token")

        // Manually set status to Shipped
        val shippedOrder = order.copy(status = OrderStatus.Shipped)
        ctx.orderRepository.update(shippedOrder)

        val result = ctx.service.shipOrder(UUID.randomUUID(), order.id)

        assertNotNull(result)
        assertEquals(OrderStatus.Shipped, result.status)
        assertEquals(0, ctx.eventPublisher.statusChangedEvents.size)
    }

    test("should throw error if shipping order that is not paid") {
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

        val order = ctx.service.createOrder(UUID.randomUUID(), "buyer-1", "buyer@test.com", "John", request, "token")

        assertFailsWith<IllegalArgumentException> {
            ctx.service.shipOrder(UUID.randomUUID(), order.id)
        }
    }

    test("should throw error if cancelling paid order") {
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

        val order = ctx.service.createOrder(UUID.randomUUID(), "buyer-1", "buyer@test.com", "John", request, "token")

        // Manually set status to Paid
        val paidOrder = order.copy(status = OrderStatus.Paid)
        ctx.orderRepository.update(paidOrder)

        assertFailsWith<IllegalArgumentException> {
            ctx.service.cancelOrder(UUID.randomUUID(), order.id, "buyer-1", "Reason")
        }
    }

    test("should throw error if cancelling shipped order") {
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

        val order = ctx.service.createOrder(UUID.randomUUID(), "buyer-1", "buyer@test.com", "John", request, "token")

        // Manually set status to Shipped
        val shippedOrder = order.copy(status = OrderStatus.Shipped)
        ctx.orderRepository.update(shippedOrder)

        assertFailsWith<IllegalArgumentException> {
            ctx.service.cancelOrder(UUID.randomUUID(), order.id, "buyer-1", "Reason")
        }
    }

    test("should transition to awaiting validation and publish events") {
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

        val order = ctx.service.createOrder(UUID.randomUUID(), "buyer-1", "buyer@test.com", "John", request, "token")

        val updatedOrder = ctx.service.transitionToAwaitingValidation(order.id)

        assertNotNull(updatedOrder)
        assertEquals(OrderStatus.AwaitingValidation, updatedOrder.status)
        assertEquals(1, ctx.eventPublisher.awaitingValidationEvents.size)
        assertEquals(order.id, ctx.eventPublisher.awaitingValidationEvents[0].orderId)
        assertEquals(1, ctx.eventPublisher.awaitingValidationEvents[0].items.size)
        assertEquals(1L, ctx.eventPublisher.awaitingValidationEvents[0].items[0].menuItemId)
        assertEquals(2, ctx.eventPublisher.awaitingValidationEvents[0].items[0].quantity)

        val statusChangedEvent = ctx.eventPublisher.statusChangedEvents.last()
        assertEquals(OrderStatus.Submitted, statusChangedEvent.oldStatus)
        assertEquals(OrderStatus.AwaitingValidation, statusChangedEvent.newStatus)
    }

    test("should cancel order due to payment failure and publish events") {
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

        val order = ctx.service.createOrder(UUID.randomUUID(), "buyer-1", "buyer@test.com", "John", request, "token")

        // Move to StockConfirmed
        val stockConfirmedOrder = order.copy(status = OrderStatus.StockConfirmed)
        ctx.orderRepository.update(stockConfirmedOrder)

        val cancelledOrder = ctx.service.cancelOrderDueToPaymentFailure(order.id, "Insufficient funds", PaymentFailureCode.INSUFFICIENT_FUNDS)

        assertNotNull(cancelledOrder)
        assertEquals(OrderStatus.Cancelled, cancelledOrder.status)
        assertEquals("Payment failed (INSUFFICIENT_FUNDS): Insufficient funds", cancelledOrder.description)

        assertEquals(1, ctx.eventPublisher.cancelledEvents.size)
        assertEquals("Payment failed (INSUFFICIENT_FUNDS): Insufficient funds", ctx.eventPublisher.cancelledEvents[0].reason)

        assertEquals(1, ctx.eventPublisher.stockReturnedEvents.size)
        assertEquals(order.id, ctx.eventPublisher.stockReturnedEvents[0].orderId)
    }
}

package io.ktor.foodies.order.tracking

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.foodies.events.common.CardBrand
import io.ktor.foodies.events.order.OrderStatus
import io.ktor.foodies.order.Address
import io.ktor.foodies.order.TestContext
import io.ktor.foodies.order.createTestContext
import io.ktor.foodies.order.placement.CreateOrder
import io.ktor.foodies.order.placement.CreateOrderItem
import io.ktor.foodies.order.placement.PaymentDetails
import io.ktor.foodies.server.test.createUserToken
import io.ktor.foodies.server.test.installTestAuth
import io.ktor.foodies.server.test.testApplication
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val trackingRoutesSpec by testSuite {
    testApplication("PUT /orders/{id}/cancel returns 400 for malformed JSON payload") {
        val ctx = createTestContext()
        val order = createOrder(ctx)
        installTestAuth()
        install(ContentNegotiation) { json() }
        routing { trackingRoutes(ctx.trackingService) }

        val response = client.put("/orders/${order.id}/cancel") {
            bearerAuth(createUserToken(userId = order.buyerId))
            header("X-Request-Id", UUID.randomUUID().toString())
            contentType(ContentType.Application.Json)
            setBody("""{"reason":"no-closing-quote}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val updatedOrder = requireNotNull(ctx.orderRepository.findById(order.id))
        assertEquals(OrderStatus.Submitted, updatedOrder.status)
        assertTrue(ctx.trackingEventPublisher.cancelledEvents.isEmpty())
        assertTrue(ctx.trackingEventPublisher.statusChangedEvents.isEmpty())
    }

    testApplication("PUT /orders/{id}/cancel returns 400 when reason field is missing") {
        val ctx = createTestContext()
        val order = createOrder(ctx)
        installTestAuth()
        install(ContentNegotiation) { json() }
        routing { trackingRoutes(ctx.trackingService) }

        val response = client.put("/orders/${order.id}/cancel") {
            bearerAuth(createUserToken(userId = order.buyerId))
            header("X-Request-Id", UUID.randomUUID().toString())
            contentType(ContentType.Application.Json)
            setBody("{}")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val updatedOrder = requireNotNull(ctx.orderRepository.findById(order.id))
        assertEquals(OrderStatus.Submitted, updatedOrder.status)
        assertTrue(ctx.trackingEventPublisher.cancelledEvents.isEmpty())
        assertTrue(ctx.trackingEventPublisher.statusChangedEvents.isEmpty())
    }
}

private fun createOrder(ctx: TestContext) = ctx.orderRepository.create(
    CreateOrder(
        requestId = "request-1",
        buyerId = "buyer-1",
        buyerEmail = "buyer-1@test.com",
        buyerName = "Buyer One",
        deliveryAddress = Address(
            street = "Street",
            city = "City",
            state = "State",
            country = "Country",
            zipCode = "12345"
        ),
        items = listOf(
            CreateOrderItem(
                menuItemId = 1L,
                menuItemName = "Burger",
                pictureUrl = "url",
                unitPrice = BigDecimal("10.00"),
                quantity = 1
            )
        ),
        totalPrice = BigDecimal("10.00"),
        currency = "USD",
        paymentDetails = PaymentDetails(
            cardType = CardBrand.VISA,
            cardNumber = "1234567812345678",
            cardHolderName = "Buyer One",
            cardSecurityNumber = "123",
            expirationMonth = 12,
            expirationYear = 2030
        )
    )
)

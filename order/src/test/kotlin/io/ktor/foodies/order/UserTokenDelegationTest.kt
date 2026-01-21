package io.ktor.foodies.order

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.foodies.order.client.BasketItem
import io.ktor.foodies.order.client.CustomerBasket
import io.ktor.foodies.order.domain.CreateOrderRequest
import io.ktor.foodies.server.SerializableBigDecimal
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.math.BigDecimal
import java.util.*
import kotlin.test.assertEquals

private const val TEST_SECRET = "test-secret"
private const val TEST_ISSUER = "test-issuer"
private const val TEST_AUDIENCE = "foodies"

val userTokenDelegationSpec by testSuite {
    test("Order service should delegate user token to Basket service") {
        val testBuyerId = "user-123"
        val testToken = JWT.create()
            .withIssuer(TEST_ISSUER)
            .withAudience(TEST_AUDIENCE)
            .withSubject(testBuyerId)
            .withClaim("email", "user@test.com")
            .withClaim("name", "Test User")
            .sign(Algorithm.HMAC256(TEST_SECRET))

        val ctx = createTestContext()
        val orderService = ctx.service

        // We want to verify that when OrderService calls BasketClient, it passes the same token.
        // In our TestContext, InMemoryBasketClient is used.
        // Let's check if we can verify the token there.

        testApplication {
            application {
                install(ContentNegotiation) { json() }
                install(Authentication) {
                    jwt {
                        verifier(JWT.require(Algorithm.HMAC256(TEST_SECRET)).withIssuer(TEST_ISSUER).build())
                        validate { credential -> JWTPrincipal(credential.payload) }
                    }
                }
                routing {
                    orderRoutes(orderService)
                }
            }

            // Mock the basket response in our InMemoryBasketClient
            ctx.basketClient.basket = CustomerBasket(
                buyerId = testBuyerId,
                items = listOf(
                    BasketItem(1L, "Burger", "url", BigDecimal("10.00"), 2)
                )
            )

            val orderRequest = CreateOrderRequest(
                street = "Street",
                city = "City",
                state = "State",
                country = "Country",
                zipCode = "12345",
                paymentDetails = io.ktor.foodies.order.domain.PaymentDetails(
                    cardType = io.ktor.foodies.events.common.CardBrand.VISA,
                    cardHolderName = "Test User",
                    cardNumber = "1234567890123456",
                    expirationMonth = 12,
                    expirationYear = 2030,
                    cardSecurityNumber = "123"
                )
            )

            val response = client.post("/orders") {
                header("Authorization", "Bearer $testToken")
                header("X-Request-Id", UUID.randomUUID().toString())
                contentType(ContentType.Application.Json)
                setBody(orderRequest)
            }

            assertEquals(HttpStatusCode.Created, response.status)
            
            // Verify that the token was delegated to the basket client
            assertEquals(testToken, ctx.basketClient.lastToken, "Token should be delegated to BasketClient")
        }
    }
}

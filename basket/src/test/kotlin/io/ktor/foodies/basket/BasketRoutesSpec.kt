package io.ktor.foodies.basket

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.github.dockerjava.api.model.ErrorResponse
import de.infix.testBalloon.framework.core.TestExecutionScope
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.framework.shared.TestRegistering
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.foodies.server.ValidationException
import io.ktor.foodies.server.test.testApplication
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TEST_SECRET = "test-jwt-secret-for-testing-only"
private const val TEST_ISSUER = "test-issuer"
private const val TEST_AUDIENCE = "test-audience"

/**
 * Creates a JWT token for testing purposes.
 */
private fun createTestToken(
    subject: String,
    issuer: String = TEST_ISSUER,
    audience: String = TEST_AUDIENCE,
): String = JWT.create()
    .withSubject(subject)
    .withIssuer(issuer)
    .withAudience(audience)
    .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
    .sign(Algorithm.HMAC256(TEST_SECRET))

/**
 * Configures test application with mock JWT authentication.
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
private fun Application.testApp(module: BasketModule) {
    install(ServerContentNegotiation) { json() }

    install(StatusPages) {
        exception<ValidationException> { call, cause ->
            call.respondText(cause.message, status = HttpStatusCode.BadRequest)
        }
    }

    install(Authentication) {
        jwt {
            verifier(
                JWT.require(Algorithm.HMAC256(TEST_SECRET))
                    .withIssuer(TEST_ISSUER)
                    .withAudience(TEST_AUDIENCE)
                    .build()
            )
            validate { credential ->
                if (credential.payload.subject != null) {
                    JWTPrincipal(credential.payload)
                } else null
            }
        }
    }

    routing {
        get("/healthz") { call.respond(HttpStatusCode.OK) }
        healthzReady(module.redisCommands)
        basketRoutes(module.basketService)
    }
}

private fun ApplicationTestBuilder.jsonClient() = createClient {
    install(ContentNegotiation) { json() }
}

/**
 * Test context holding in-memory implementations for basket routes tests.
 */
private data class RoutesTestContext(
    val menuClient: InMemoryMenuClient,
    val repository: InMemoryBasketRepository,
    val service: BasketService,
    val module: BasketModule,
)

@OptIn(ExperimentalLettuceCoroutinesApi::class)
private fun createRoutesTestContext(): RoutesTestContext {
    val menuClient = InMemoryMenuClient()
    val repository = InMemoryBasketRepository()
    val service = BasketServiceImpl(repository, menuClient)
    val mockRedisCommands = mockk<RedisCoroutinesCommands<String, String>> {
        coEvery { ping() } returns "PONG"
    }
    val module = BasketModule(basketService = service, consumers = emptyList(), redisCommands = mockRedisCommands)
    return RoutesTestContext(menuClient, repository, service, module)
}

@TestRegistering
private fun TestSuite.testBasketRoutes(
    name: String,
    setup: (RoutesTestContext) -> Unit = {},
    block: suspend context(TestExecutionScope) ApplicationTestBuilder.(RoutesTestContext) -> Unit,
) = testApplication(name) {
    val ctx = createRoutesTestContext()
    setup(ctx)
    application { testApp(ctx.module) }
    block(ctx)
}

val basketRoutesSpec by testSuite {
    val testUserId = "user-123"
    val testToken = createTestToken(testUserId)

    testSuite("GET /healthz") {
        testBasketRoutes("returns 200 OK") { _ ->
            val response = jsonClient().get("/healthz")
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    testSuite("GET /healthz/ready") {
        testBasketRoutes("returns 200 OK with UP status when Redis is healthy") { _ ->
            val response = jsonClient().get("/healthz/ready")
            assertEquals(HttpStatusCode.OK, response.status)
            val healthStatus = response.body<HealthStatus>()
            assertEquals("UP", healthStatus.status)
        }
    }

    testSuite("GET /basket") {
        testBasketRoutes("returns empty basket for new user") { _ ->
            val response = jsonClient().get("/basket") {
                bearerAuth(testToken)
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val basket = response.body<CustomerBasket>()
            assertEquals(testUserId, basket.buyerId)
            assertTrue(basket.items.isEmpty())
        }

        testBasketRoutes("returns 401 when not authenticated") { _ ->
            val response = jsonClient().get("/basket")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

        testBasketRoutes("returns existing basket with items", setup = { ctx ->
            ctx.menuClient.addMenuItem(
                MenuItem(
                    id = 1L,
                    name = "Pizza",
                    description = "Delicious",
                    imageUrl = "https://example.com/pizza.jpg",
                    price = BigDecimal("10.00")
                )
            )
        }) { ctx ->
            // First add an item
            jsonClient().post("/basket/items") {
                bearerAuth(testToken)
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId = 1L, quantity = 2))
            }

            val response = jsonClient().get("/basket") {
                bearerAuth(testToken)
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val basket = response.body<CustomerBasket>()
            assertEquals(1, basket.items.size)
            assertEquals(1L, basket.items[0].menuItemId)
            assertEquals(2, basket.items[0].quantity)
        }
    }

    testSuite("POST /basket/items") {
        testBasketRoutes("adds item to empty basket", setup = { ctx ->
            ctx.menuClient.addMenuItem(
                MenuItem(
                    id = 1L,
                    name = "Pizza Margherita",
                    description = "Classic tomato and mozzarella",
                    imageUrl = "https://example.com/pizza.jpg",
                    price = BigDecimal("12.99")
                )
            )
        }) { _ ->
            val response = jsonClient().post("/basket/items") {
                bearerAuth(testToken)
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId = 1L, quantity = 2))
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val basket = response.body<CustomerBasket>()
            assertEquals(testUserId, basket.buyerId)
            assertEquals(1, basket.items.size)
            assertEquals(1L, basket.items[0].menuItemId)
            assertEquals("Pizza Margherita", basket.items[0].menuItemName)
            assertEquals(2, basket.items[0].quantity)
            assertEquals(BigDecimal("12.99"), basket.items[0].unitPrice)
        }

        testBasketRoutes("increments quantity when item already in basket", setup = { ctx ->
            ctx.menuClient.addMenuItem(
                MenuItem(
                    id = 1L,
                    name = "Pizza",
                    description = "Delicious",
                    imageUrl = "https://example.com/pizza.jpg",
                    price = BigDecimal("10.00")
                )
            )
        }) { _ ->
            // Add item first time
            jsonClient().post("/basket/items") {
                bearerAuth(testToken)
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId = 1L, quantity = 2))
            }

            // Add same item again
            val response = jsonClient().post("/basket/items") {
                bearerAuth(testToken)
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId = 1L, quantity = 3))
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val basket = response.body<CustomerBasket>()
            assertEquals(1, basket.items.size)
            assertEquals(5, basket.items[0].quantity) // 2 + 3 = 5
        }

        testBasketRoutes("returns 404 when menu item does not exist") { _ ->
            val response = jsonClient().post("/basket/items") {
                bearerAuth(testToken)
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId = 999L, quantity = 1))
            }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

        testBasketRoutes("returns 400 for invalid quantity (zero)") { _ ->
            val response = jsonClient().post("/basket/items") {
                bearerAuth(testToken)
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId = 1L, quantity = 0))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = response.bodyAsText()
            assertTrue(error.contains("quantity"))
        }

        testBasketRoutes("returns 400 for negative quantity") { _ ->
            val response = jsonClient().post("/basket/items") {
                bearerAuth(testToken)
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId = 1L, quantity = -5))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

        testBasketRoutes("returns 401 when not authenticated") { _ ->
            val response = jsonClient().post("/basket/items") {
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId = 1L, quantity = 1))
            }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    testSuite("PUT /basket/items/{itemId}") {
        testBasketRoutes("updates item quantity", setup = { ctx ->
            ctx.menuClient.addMenuItem(
                MenuItem(
                    id = 1L,
                    name = "Pizza",
                    description = "Delicious",
                    imageUrl = "https://example.com/pizza.jpg",
                    price = BigDecimal("10.00")
                )
            )
        }) { _ ->
            // First add an item
            val addResponse = jsonClient().post("/basket/items") {
                bearerAuth(testToken)
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId = 1L, quantity = 1))
            }
            val addedBasket = addResponse.body<CustomerBasket>()
            val itemId = addedBasket.items[0].id

            // Update quantity
            val response = jsonClient().put("/basket/items/$itemId") {
                bearerAuth(testToken)
                contentType(ContentType.Application.Json)
                setBody(UpdateItemQuantityRequest(quantity = 10))
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val basket = response.body<CustomerBasket>()
            assertEquals(1, basket.items.size)
            assertEquals(10, basket.items[0].quantity)
        }

        testBasketRoutes("returns 404 when item not in basket") { _ ->
            val response = jsonClient().put("/basket/items/non-existent-id") {
                bearerAuth(testToken)
                contentType(ContentType.Application.Json)
                setBody(UpdateItemQuantityRequest(quantity = 5))
            }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

        testBasketRoutes("returns 400 for invalid quantity") { _ ->
            val response = jsonClient().put("/basket/items/some-id") {
                bearerAuth(testToken)
                contentType(ContentType.Application.Json)
                setBody(UpdateItemQuantityRequest(quantity = 0))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

        testBasketRoutes("returns 401 when not authenticated") { _ ->
            val response = jsonClient().put("/basket/items/some-id") {
                contentType(ContentType.Application.Json)
                setBody(UpdateItemQuantityRequest(quantity = 5))
            }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    testSuite("DELETE /basket/items/{itemId}") {
        testBasketRoutes("removes item from basket", setup = { ctx ->
            ctx.menuClient.addMenuItem(
                MenuItem(
                    id = 1L,
                    name = "Pizza",
                    description = "Delicious",
                    imageUrl = "https://example.com/pizza.jpg",
                    price = BigDecimal("10.00")
                )
            )
            ctx.menuClient.addMenuItem(
                MenuItem(
                    id = 2L,
                    name = "Pasta",
                    description = "Creamy",
                    imageUrl = "https://example.com/pasta.jpg",
                    price = BigDecimal("8.50")
                )
            )
        }) { _ ->
            // Add two items
            jsonClient().post("/basket/items") {
                bearerAuth(testToken)
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId = 1L, quantity = 1))
            }
            val basketWith2 = jsonClient().post("/basket/items") {
                bearerAuth(testToken)
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId = 2L, quantity = 2))
            }.body<CustomerBasket>()

            val itemToRemove = basketWith2.items[0].id

            // Remove first item
            val response = jsonClient().delete("/basket/items/$itemToRemove") {
                bearerAuth(testToken)
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val basket = response.body<CustomerBasket>()
            assertEquals(1, basket.items.size)
            assertEquals(2L, basket.items[0].menuItemId)
        }

        testBasketRoutes("returns 404 when item not in basket") { _ ->
            val response = jsonClient().delete("/basket/items/non-existent-id") {
                bearerAuth(testToken)
            }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

        testBasketRoutes("returns 401 when not authenticated") { _ ->
            val response = jsonClient().delete("/basket/items/some-id")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    testSuite("DELETE /basket") {
        testBasketRoutes("clears entire basket", setup = { ctx ->
            ctx.menuClient.addMenuItem(
                MenuItem(
                    id = 1L,
                    name = "Pizza",
                    description = "Delicious",
                    imageUrl = "https://example.com/pizza.jpg",
                    price = BigDecimal("10.00")
                )
            )
        }) { _ ->
            // Add an item first
            jsonClient().post("/basket/items") {
                bearerAuth(testToken)
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId = 1L, quantity = 3))
            }

            // Clear basket
            val response = jsonClient().delete("/basket") {
                bearerAuth(testToken)
            }

            assertEquals(HttpStatusCode.NoContent, response.status)

            // Verify basket is empty
            val getResponse = jsonClient().get("/basket") {
                bearerAuth(testToken)
            }
            val basket = getResponse.body<CustomerBasket>()
            assertTrue(basket.items.isEmpty())
        }

        testBasketRoutes("returns 204 even when basket is already empty") { _ ->
            val response = jsonClient().delete("/basket") {
                bearerAuth(testToken)
            }

            assertEquals(HttpStatusCode.NoContent, response.status)
        }

        testBasketRoutes("returns 401 when not authenticated") { _ ->
            val response = jsonClient().delete("/basket")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    testSuite("User isolation") {
        testBasketRoutes("different users have separate baskets", setup = { ctx ->
            ctx.menuClient.addMenuItem(
                MenuItem(
                    id = 1L,
                    name = "Pizza",
                    description = "Delicious",
                    imageUrl = "https://example.com/pizza.jpg",
                    price = BigDecimal("10.00")
                )
            )
        }) { _ ->
            val user1Token = createTestToken("user-1")
            val user2Token = createTestToken("user-2")

            // User 1 adds item
            jsonClient().post("/basket/items") {
                bearerAuth(user1Token)
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId = 1L, quantity = 5))
            }

            // User 2 adds same item with different quantity
            jsonClient().post("/basket/items") {
                bearerAuth(user2Token)
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId = 1L, quantity = 2))
            }

            // Verify user 1's basket
            val basket1 = jsonClient().get("/basket") {
                bearerAuth(user1Token)
            }.body<CustomerBasket>()
            assertEquals("user-1", basket1.buyerId)
            assertEquals(5, basket1.items[0].quantity)

            // Verify user 2's basket
            val basket2 = jsonClient().get("/basket") {
                bearerAuth(user2Token)
            }.body<CustomerBasket>()
            assertEquals("user-2", basket2.buyerId)
            assertEquals(2, basket2.items[0].quantity)
        }
    }
}

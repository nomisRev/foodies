package io.ktor.foodies.basket

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.github.dockerjava.api.model.ErrorResponse
import com.redis.testcontainers.RedisContainer
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.aroundEachTest
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
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
import io.lettuce.core.RedisClient
import io.lettuce.core.api.coroutines
import java.math.BigDecimal
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TEST_SECRET = "test-jwt-secret-for-end-to-end-testing"
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
 * Configures test application with mock JWT authentication and real Redis.
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
private fun Application.testApp(module: BasketModule) {
    install(ServerContentNegotiation) { json() }

    install(StatusPages) {
        exception<io.ktor.server.plugins.BadRequestException> { call, cause ->
            call.respondText(cause.message ?: "Bad Request", status = HttpStatusCode.BadRequest)
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
 * End-to-end tests for the Basket service using real Redis and mock Menu service.
 *
 * These tests verify the complete flow from HTTP routes through the service layer
 * to actual Redis storage, ensuring all components work together correctly.
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
val basketEndToEndSpec by testSuite {
    // Use real Redis via Testcontainers
    val container = testFixture {
        RedisContainer("redis:7-alpine").apply { start() }
    }

    val redisClient = testFixture {
        RedisClient.create(container().redisURI)
    }

    testSuite(
        "End-to-end basket operations with real Redis",
        testConfig = TestConfig.aroundEachTest { test ->
            // Clean up Redis before each test
            val connection = redisClient().connect()
            connection.sync().flushall()
            connection.close()
            test()
        }
    ) {
        testApplication("complete shopping flow: add items, update quantity, remove item, clear basket") {
            val menuClient = InMemoryMenuClient()
            val connection = redisClient().connect()
            val redisCommands = connection.coroutines()
            val repository = RedisBasketRepository(redisCommands)
            val service = BasketServiceImpl(repository, menuClient)
            val module = BasketModule(basketService = service, consumers = emptyList(), redisCommands = redisCommands)

            val testUserId = "e2e-user-123"
            val testToken = createTestToken(testUserId)

            // Setup menu items
            menuClient.addMenuItem(
                MenuItem(id = 1L, name = "Margherita Pizza", description = "Classic tomato and mozzarella", imageUrl = "https://example.com/pizza.jpg", price = BigDecimal("12.99"))
            )
            menuClient.addMenuItem(
                MenuItem(id = 2L, name = "Pasta Carbonara", description = "Creamy bacon pasta", imageUrl = "https://example.com/pasta.jpg", price = BigDecimal("14.50"))
            )
            menuClient.addMenuItem(
                MenuItem(id = 3L, name = "Tiramisu", description = "Italian dessert", imageUrl = "https://example.com/tiramisu.jpg", price = BigDecimal("7.99"))
            )

            application { testApp(module) }

            // Step 1: Start with empty basket
            val emptyBasket = jsonClient().get("/basket") {
                bearerAuth(testToken)
            }.body<CustomerBasket>()
            assertEquals(testUserId, emptyBasket.buyerId)
            assertTrue(emptyBasket.items.isEmpty())

            // Step 2: Add first item (pizza)
            val afterAddPizza = jsonClient().post("/basket/items") {
                bearerAuth(testToken)
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId = 1L, quantity = 2))
            }.body<CustomerBasket>()
            assertEquals(1, afterAddPizza.items.size)
            assertEquals("Margherita Pizza", afterAddPizza.items[0].menuItemName)
            assertEquals(2, afterAddPizza.items[0].quantity)
            assertEquals(BigDecimal("12.99"), afterAddPizza.items[0].unitPrice)

            // Step 3: Add second item (pasta)
            val afterAddPasta = jsonClient().post("/basket/items") {
                bearerAuth(testToken)
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId = 2L, quantity = 1))
            }.body<CustomerBasket>()
            assertEquals(2, afterAddPasta.items.size)

            // Step 4: Add more of the same pizza (should increment quantity)
            val afterAddMorePizza = jsonClient().post("/basket/items") {
                bearerAuth(testToken)
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId = 1L, quantity = 3))
            }.body<CustomerBasket>()
            assertEquals(2, afterAddMorePizza.items.size) // Still 2 items
            val pizzaItem = afterAddMorePizza.items.find { it.menuItemId == 1L }
            assertNotNull(pizzaItem)
            assertEquals(5, pizzaItem.quantity) // 2 + 3 = 5

            // Step 5: Update quantity of pasta
            val pastaItem = afterAddMorePizza.items.find { it.menuItemId == 2L }
            assertNotNull(pastaItem)
            val afterUpdatePasta = jsonClient().put("/basket/items/${pastaItem.id}") {
                bearerAuth(testToken)
                contentType(ContentType.Application.Json)
                setBody(UpdateItemQuantityRequest(quantity = 4))
            }.body<CustomerBasket>()
            val updatedPastaItem = afterUpdatePasta.items.find { it.menuItemId == 2L }
            assertNotNull(updatedPastaItem)
            assertEquals(4, updatedPastaItem.quantity)

            // Step 6: Verify total price
            // Pizza: 5 * 12.99 = 64.95
            // Pasta: 4 * 14.50 = 58.00
            // Total: 122.95
            assertEquals(BigDecimal("122.95"), afterUpdatePasta.totalPrice())

            // Step 7: Add dessert
            val afterAddDessert = jsonClient().post("/basket/items") {
                bearerAuth(testToken)
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId = 3L, quantity = 2))
            }.body<CustomerBasket>()
            assertEquals(3, afterAddDessert.items.size)

            // Step 8: Remove pasta from basket
            val afterRemovePasta = jsonClient().delete("/basket/items/${pastaItem.id}") {
                bearerAuth(testToken)
            }.body<CustomerBasket>()
            assertEquals(2, afterRemovePasta.items.size)
            assertTrue(afterRemovePasta.items.none { it.menuItemId == 2L })

            // Step 9: Verify persistence - get basket again should return same data
            val persistedBasket = jsonClient().get("/basket") {
                bearerAuth(testToken)
            }.body<CustomerBasket>()
            assertEquals(2, persistedBasket.items.size)
            assertEquals(afterRemovePasta.items.map { it.id }.toSet(), persistedBasket.items.map { it.id }.toSet())

            // Step 10: Clear basket
            val clearResponse = jsonClient().delete("/basket") {
                bearerAuth(testToken)
            }
            assertEquals(HttpStatusCode.NoContent, clearResponse.status)

            // Step 11: Verify basket is empty
            val finalBasket = jsonClient().get("/basket") {
                bearerAuth(testToken)
            }.body<CustomerBasket>()
            assertTrue(finalBasket.items.isEmpty())

            connection.close()
        }

        testApplication("user isolation: different users have separate baskets in Redis") {
            val menuClient = InMemoryMenuClient()
            val connection = redisClient().connect()
            val redisCommands = connection.coroutines()
            val repository = RedisBasketRepository(redisCommands)
            val service = BasketServiceImpl(repository, menuClient)
            val module = BasketModule(basketService = service, consumers = emptyList(), redisCommands = redisCommands)

            val user1Id = "e2e-user-1"
            val user2Id = "e2e-user-2"
            val user1Token = createTestToken(user1Id)
            val user2Token = createTestToken(user2Id)

            // Setup menu items
            menuClient.addMenuItem(
                MenuItem(id = 1L, name = "Pizza", description = "Delicious", imageUrl = "https://example.com/pizza.jpg", price = BigDecimal("15.00"))
            )
            menuClient.addMenuItem(
                MenuItem(id = 2L, name = "Burger", description = "Juicy", imageUrl = "https://example.com/burger.jpg", price = BigDecimal("12.00"))
            )

            application { testApp(module) }

            // User 1 adds pizza
            jsonClient().post("/basket/items") {
                bearerAuth(user1Token)
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId = 1L, quantity = 3))
            }

            // User 2 adds burger
            jsonClient().post("/basket/items") {
                bearerAuth(user2Token)
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId = 2L, quantity = 5))
            }

            // Verify user 1's basket
            val user1Basket = jsonClient().get("/basket") {
                bearerAuth(user1Token)
            }.body<CustomerBasket>()
            assertEquals(user1Id, user1Basket.buyerId)
            assertEquals(1, user1Basket.items.size)
            assertEquals(1L, user1Basket.items[0].menuItemId)
            assertEquals(3, user1Basket.items[0].quantity)

            // Verify user 2's basket
            val user2Basket = jsonClient().get("/basket") {
                bearerAuth(user2Token)
            }.body<CustomerBasket>()
            assertEquals(user2Id, user2Basket.buyerId)
            assertEquals(1, user2Basket.items.size)
            assertEquals(2L, user2Basket.items[0].menuItemId)
            assertEquals(5, user2Basket.items[0].quantity)

            // User 1 clears their basket - should not affect user 2
            jsonClient().delete("/basket") {
                bearerAuth(user1Token)
            }

            // Verify user 1 has empty basket
            val user1AfterClear = jsonClient().get("/basket") {
                bearerAuth(user1Token)
            }.body<CustomerBasket>()
            assertTrue(user1AfterClear.items.isEmpty())

            // Verify user 2 still has their items
            val user2AfterUser1Clear = jsonClient().get("/basket") {
                bearerAuth(user2Token)
            }.body<CustomerBasket>()
            assertEquals(1, user2AfterUser1Clear.items.size)
            assertEquals(5, user2AfterUser1Clear.items[0].quantity)

            connection.close()
        }

        testApplication("error handling: menu item not found returns 404") {
            val menuClient = InMemoryMenuClient()
            val connection = redisClient().connect()
            val redisCommands = connection.coroutines()
            val repository = RedisBasketRepository(redisCommands)
            val service = BasketServiceImpl(repository, menuClient)
            val module = BasketModule(basketService = service, consumers = emptyList(), redisCommands = redisCommands)

            val testToken = createTestToken("e2e-user-error")
            // Don't add any menu items

            application { testApp(module) }

            val response = jsonClient().post("/basket/items") {
                bearerAuth(testToken)
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId = 999L, quantity = 1))
            }

            assertEquals(HttpStatusCode.NotFound, response.status)

            connection.close()
        }

        testApplication("error handling: update non-existent item returns 404") {
            val menuClient = InMemoryMenuClient()
            val connection = redisClient().connect()
            val redisCommands = connection.coroutines()
            val repository = RedisBasketRepository(redisCommands)
            val service = BasketServiceImpl(repository, menuClient)
            val module = BasketModule(basketService = service, consumers = emptyList(), redisCommands = redisCommands)

            val testToken = createTestToken("e2e-user-error")

            application { testApp(module) }

            val response = jsonClient().put("/basket/items/non-existent-item-id") {
                bearerAuth(testToken)
                contentType(ContentType.Application.Json)
                setBody(UpdateItemQuantityRequest(quantity = 5))
            }

            assertEquals(HttpStatusCode.NotFound, response.status)

            connection.close()
        }

        testApplication("error handling: remove non-existent item returns 404") {
            val menuClient = InMemoryMenuClient()
            val connection = redisClient().connect()
            val redisCommands = connection.coroutines()
            val repository = RedisBasketRepository(redisCommands)
            val service = BasketServiceImpl(repository, menuClient)
            val module = BasketModule(basketService = service, consumers = emptyList(), redisCommands = redisCommands)

            val testToken = createTestToken("e2e-user-error")

            application { testApp(module) }

            val response = jsonClient().delete("/basket/items/non-existent-item-id") {
                bearerAuth(testToken)
            }

            assertEquals(HttpStatusCode.NotFound, response.status)

            connection.close()
        }

        testApplication("error handling: invalid quantity returns 400") {
            val menuClient = InMemoryMenuClient()
            val connection = redisClient().connect()
            val redisCommands = connection.coroutines()
            val repository = RedisBasketRepository(redisCommands)
            val service = BasketServiceImpl(repository, menuClient)
            val module = BasketModule(basketService = service, consumers = emptyList(), redisCommands = redisCommands)

            val testToken = createTestToken("e2e-user-validation")
            menuClient.addMenuItem(
                MenuItem(id = 1L, name = "Pizza", description = "Delicious", imageUrl = "https://example.com/pizza.jpg", price = BigDecimal("10.00"))
            )

            application { testApp(module) }

            val response = jsonClient().post("/basket/items") {
                bearerAuth(testToken)
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId = 1L, quantity = 0))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = response.bodyAsText()
            assertTrue(error.contains("quantity"))

            connection.close()
        }

        testApplication("health check endpoints work with real Redis") {
            val menuClient = InMemoryMenuClient()
            val connection = redisClient().connect()
            val redisCommands = connection.coroutines()
            val repository = RedisBasketRepository(redisCommands)
            val service = BasketServiceImpl(repository, menuClient)
            val module = BasketModule(basketService = service, consumers = emptyList(), redisCommands = redisCommands)

            application { testApp(module) }

            // Basic health check
            val healthResponse = jsonClient().get("/healthz")
            assertEquals(HttpStatusCode.OK, healthResponse.status)

            // Readiness check with Redis
            val readyResponse = jsonClient().get("/healthz/ready")
            assertEquals(HttpStatusCode.OK, readyResponse.status)
            val healthStatus = readyResponse.body<HealthStatus>()
            assertEquals("UP", healthStatus.status)

            connection.close()
        }

        testApplication("data persists correctly: basket survives service reconnection simulation") {
            val menuClient = InMemoryMenuClient()
            val connection = redisClient().connect()
            val redisCommands = connection.coroutines()
            val repository = RedisBasketRepository(redisCommands)
            val service = BasketServiceImpl(repository, menuClient)
            val module = BasketModule(basketService = service, consumers = emptyList(), redisCommands = redisCommands)

            val testUserId = "e2e-persistence-user"
            val testToken = createTestToken(testUserId)

            menuClient.addMenuItem(
                MenuItem(id = 1L, name = "Pizza", description = "Delicious", imageUrl = "https://example.com/pizza.jpg", price = BigDecimal("10.00"))
            )

            application { testApp(module) }

            // Add item to basket
            jsonClient().post("/basket/items") {
                bearerAuth(testToken)
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId = 1L, quantity = 7))
            }

            // Close original connection
            connection.close()

            // Create a new connection (simulating service restart)
            val newConnection = redisClient().connect()
            val newRepository = RedisBasketRepository(newConnection.coroutines())

            // Verify data persists
            val persistedBasket = newRepository.getBasket(testUserId)
            assertNotNull(persistedBasket)
            assertEquals(1, persistedBasket.items.size)
            assertEquals(1L, persistedBasket.items[0].menuItemId)
            assertEquals(7, persistedBasket.items[0].quantity)

            newConnection.close()
        }
    }
}

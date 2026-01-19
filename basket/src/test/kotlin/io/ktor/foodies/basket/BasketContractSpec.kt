package io.ktor.foodies.basket

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.foodies.server.test.ctxSuite
import io.ktor.foodies.server.test.jsonClient
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Common test menu items
private val MARGHERITA_PIZZA =
    MenuItem(
        id = 1L,
        name = "Margherita Pizza",
        description = "Classic tomato and mozzarella",
        imageUrl = "https://example.com/pizza.jpg",
        price = BigDecimal("12.99"),
        stock = 10,
    )

private val PASTA_CARBONARA =
    MenuItem(
        id = 2L,
        name = "Pasta Carbonara",
        description = "Creamy bacon pasta",
        imageUrl = "https://example.com/pasta.jpg",
        price = BigDecimal("14.50"),
        stock = 5,
    )

private val TIRAMISU =
    MenuItem(
        id = 3L,
        name = "Tiramisu",
        description = "Italian dessert",
        imageUrl = "https://example.com/tiramisu.jpg",
        price = BigDecimal("7.99"),
        stock = 7,
    )

private val TEST_PIZZA =
    MenuItem(
        id = 1L,
        name = "Pizza",
        description = "Delicious",
        imageUrl = "https://example.com/pizza.jpg",
        price = BigDecimal("15.00"),
        stock = 100,
    )

private val TEST_BURGER =
    MenuItem(
        id = 2L,
        name = "Burger",
        description = "Juicy",
        imageUrl = "https://example.com/burger.jpg",
        price = BigDecimal("12.00"),
        stock = 50,
    )

private val SIMPLE_PIZZA =
    MenuItem(
        id = 1L,
        name = "Pizza",
        description = "Delicious",
        imageUrl = "https://example.com/pizza.jpg",
        price = BigDecimal("10.00"),
        stock = 100,
    )

/**
 * End-to-end tests for the Basket service using real Redis and mock Menu service.
 *
 * These tests verify the complete flow from HTTP routes through the service layer to actual Redis
 * storage, ensuring all components work together correctly.
 */
val basketContractSpec by
    ctxSuite(context = { serviceContext() }) {
        testBasketService(
            "complete shopping flow: add items, update quantity, remove item, clear basket"
        ) { module ->
            val testUserId = "e2e-user-123"
            val testToken = createTestToken(testUserId)

            module.menuClient.addMenuItem(MARGHERITA_PIZZA)
            module.menuClient.addMenuItem(PASTA_CARBONARA)
            module.menuClient.addMenuItem(TIRAMISU)

            // Step 1: Start with empty basket
            val emptyBasket =
                jsonClient().get("/basket") { bearerAuth(testToken) }.body<CustomerBasket>()
            assertEquals(testUserId, emptyBasket.buyerId)
            assertTrue(emptyBasket.items.isEmpty())

            // Step 2: Add first item (pizza)
            val afterAddPizza =
                jsonClient()
                    .post("/basket/items") {
                        bearerAuth(testToken)
                        contentType(ContentType.Application.Json)
                        setBody(AddItemRequest(menuItemId = 1L, quantity = 2))
                    }
                    .body<CustomerBasket>()
            assertEquals(1, afterAddPizza.items.size)
            assertEquals("Margherita Pizza", afterAddPizza.items[0].menuItemName)
            assertEquals(2, afterAddPizza.items[0].quantity)
            assertEquals(BigDecimal("12.99"), afterAddPizza.items[0].unitPrice)

            // Step 3: Add second item (pasta)
            val afterAddPasta =
                jsonClient()
                    .post("/basket/items") {
                        bearerAuth(testToken)
                        contentType(ContentType.Application.Json)
                        setBody(AddItemRequest(menuItemId = 2L, quantity = 1))
                    }
                    .body<CustomerBasket>()
            assertEquals(2, afterAddPasta.items.size)

            // Step 4: Add more of the same pizza (should increment quantity)
            val afterAddMorePizza =
                jsonClient()
                    .post("/basket/items") {
                        bearerAuth(testToken)
                        contentType(ContentType.Application.Json)
                        setBody(AddItemRequest(menuItemId = 1L, quantity = 3))
                    }
                    .body<CustomerBasket>()
            assertEquals(2, afterAddMorePizza.items.size) // Still 2 items
            val pizzaItem = afterAddMorePizza.items.find { it.menuItemId == 1L }
            assertNotNull(pizzaItem)
            assertEquals(5, pizzaItem.quantity) // 2 + 3 = 5

            // Step 5: Update quantity of pasta
            val pastaItem = afterAddMorePizza.items.find { it.menuItemId == 2L }
            assertNotNull(pastaItem)
            val afterUpdatePasta =
                jsonClient()
                    .put("/basket/items/${pastaItem.id}") {
                        bearerAuth(testToken)
                        contentType(ContentType.Application.Json)
                        setBody(UpdateItemQuantityRequest(quantity = 4))
                    }
                    .body<CustomerBasket>()
            val updatedPastaItem = afterUpdatePasta.items.find { it.menuItemId == 2L }
            assertNotNull(updatedPastaItem)
            assertEquals(4, updatedPastaItem.quantity)

            // Step 6: Verify total price
            // Pizza: 5 * 12.99 = 64.95
            // Pasta: 4 * 14.50 = 58.00
            // Total: 122.95
            assertEquals(BigDecimal("122.95"), afterUpdatePasta.totalPrice())

            // Step 7: Add dessert
            val afterAddDessert =
                jsonClient()
                    .post("/basket/items") {
                        bearerAuth(testToken)
                        contentType(ContentType.Application.Json)
                        setBody(AddItemRequest(menuItemId = 3L, quantity = 2))
                    }
                    .body<CustomerBasket>()
            assertEquals(3, afterAddDessert.items.size)

            // Step 8: Remove pasta from basket
            val afterRemovePasta =
                jsonClient()
                    .delete("/basket/items/${pastaItem.id}") { bearerAuth(testToken) }
                    .body<CustomerBasket>()
            assertEquals(2, afterRemovePasta.items.size)
            assertTrue(afterRemovePasta.items.none { it.menuItemId == 2L })

            // Step 9: Verify persistence - get basket again should return same data
            val persistedBasket =
                jsonClient().get("/basket") { bearerAuth(testToken) }.body<CustomerBasket>()
            assertEquals(2, persistedBasket.items.size)
            assertEquals(
                afterRemovePasta.items.map { it.id }.toSet(),
                persistedBasket.items.map { it.id }.toSet(),
            )

            // Step 10: Clear basket
            val clearResponse = jsonClient().delete("/basket") { bearerAuth(testToken) }
            assertEquals(HttpStatusCode.NoContent, clearResponse.status)

            // Step 11: Verify basket is empty
            val finalBasket =
                jsonClient().get("/basket") { bearerAuth(testToken) }.body<CustomerBasket>()
            assertTrue(finalBasket.items.isEmpty())
        }

        testBasketService("user isolation: different users have separate baskets") { module ->
            val user1Id = "e2e-user-1"
            val user2Id = "e2e-user-2"
            val user1Token = createTestToken(user1Id)
            val user2Token = createTestToken(user2Id)

            module.menuClient.addMenuItem(TEST_PIZZA)
            module.menuClient.addMenuItem(TEST_BURGER)

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
            val user1Basket =
                jsonClient().get("/basket") { bearerAuth(user1Token) }.body<CustomerBasket>()
            assertEquals(user1Id, user1Basket.buyerId)
            assertEquals(1, user1Basket.items.size)
            assertEquals(1L, user1Basket.items[0].menuItemId)
            assertEquals(3, user1Basket.items[0].quantity)

            // Verify user 2's basket
            val user2Basket =
                jsonClient().get("/basket") { bearerAuth(user2Token) }.body<CustomerBasket>()
            assertEquals(user2Id, user2Basket.buyerId)
            assertEquals(1, user2Basket.items.size)
            assertEquals(2L, user2Basket.items[0].menuItemId)
            assertEquals(5, user2Basket.items[0].quantity)

            // User 1 clears their basket - should not affect user 2
            jsonClient().delete("/basket") { bearerAuth(user1Token) }

            // Verify user 1 has empty basket
            val user1AfterClear =
                jsonClient().get("/basket") { bearerAuth(user1Token) }.body<CustomerBasket>()
            assertTrue(user1AfterClear.items.isEmpty())

            // Verify user 2 still has their items
            val user2AfterUser1Clear =
                jsonClient().get("/basket") { bearerAuth(user2Token) }.body<CustomerBasket>()
            assertEquals(1, user2AfterUser1Clear.items.size)
            assertEquals(5, user2AfterUser1Clear.items[0].quantity)
        }

        testBasketService("error handling: menu item not found returns 404") { module ->
            val testToken = createTestToken("e2e-user-error")
            // Don't add any menu items

            val response =
                jsonClient().post("/basket/items") {
                    bearerAuth(testToken)
                    contentType(ContentType.Application.Json)
                    setBody(AddItemRequest(menuItemId = 999L, quantity = 1))
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

        testBasketService("error handling: update non-existent item returns 404") { module ->
            val testToken = createTestToken("e2e-user-error")

            val response =
                jsonClient().put("/basket/items/non-existent-item-id") {
                    bearerAuth(testToken)
                    contentType(ContentType.Application.Json)
                    setBody(UpdateItemQuantityRequest(quantity = 5))
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

        testBasketService("error handling: remove non-existent item returns 404") { module ->
            val testToken = createTestToken("e2e-user-error")

            val response =
                jsonClient().delete("/basket/items/non-existent-item-id") { bearerAuth(testToken) }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

        testBasketService("error handling: invalid quantity returns 400") { module ->
            val testToken = createTestToken("e2e-user-validation")
            module.menuClient.addMenuItem(SIMPLE_PIZZA)

            val response =
                jsonClient().post("/basket/items") {
                    bearerAuth(testToken)
                    contentType(ContentType.Application.Json)
                    setBody(AddItemRequest(menuItemId = 1L, quantity = 0))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = response.bodyAsText()
            assertTrue(error.contains("quantity"))
        }

        testBasketService(
            "data persists correctly: basket survives Redis reconnection simulation"
        ) { module ->
            val testUserId = "e2e-persistence-user"
            val testToken = createTestToken(testUserId)

            module.menuClient.addMenuItem(SIMPLE_PIZZA)

            // Add item to basket
            jsonClient().post("/basket/items") {
                bearerAuth(testToken)
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId = 1L, quantity = 7))
            }

            // Verify data persists by fetching basket again
            val persistedBasket =
                jsonClient().get("/basket") { bearerAuth(testToken) }.body<CustomerBasket>()
            assertEquals(testUserId, persistedBasket.buyerId)
            assertEquals(1, persistedBasket.items.size)
            assertEquals(1L, persistedBasket.items[0].menuItemId)
            assertEquals(7, persistedBasket.items[0].quantity)
        }
    }

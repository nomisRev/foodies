package io.ktor.foodies.basket

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.foodies.server.test.createUserToken
import io.ktor.foodies.server.test.ctxSuite
import io.ktor.foodies.server.test.jsonClient
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val basketRoutesSpec by
    ctxSuite(context = { serviceContext() }) {
        val testUserId = "user-123"
        val testToken = createUserToken(userId = testUserId)

        testSuite("GET /basket") {
            testBasketService("returns empty basket for new user") { _ ->
                val response = jsonClient().get("/basket") { bearerAuth(testToken) }

                assertEquals(HttpStatusCode.OK, response.status)
                val basket = response.body<CustomerBasket>()
                assertEquals(testUserId, basket.buyerId)
                assertTrue(basket.items.isEmpty())
            }

            testBasketService("returns 401 when not authenticated") { _ ->
                val response = jsonClient().get("/basket")
                assertEquals(HttpStatusCode.Unauthorized, response.status)
            }

            testBasketService("returns existing basket with items") { module ->
                module.menuClient.addMenuItem(
                    MenuItem(
                        id = 1L,
                        name = "Pizza",
                        description = "Delicious",
                        imageUrl = "https://example.com/pizza.jpg",
                        price = BigDecimal("10.00"),
                        stock = 100,
                    )
                )

                // First add an item
                jsonClient().post("/basket/items") {
                    bearerAuth(testToken)
                    contentType(ContentType.Application.Json)
                    setBody(AddItemRequest(menuItemId = 1L, quantity = 2))
                }

                val response = jsonClient().get("/basket") { bearerAuth(testToken) }

                assertEquals(HttpStatusCode.OK, response.status)
                val basket = response.body<CustomerBasket>()
                assertEquals(1, basket.items.size)
                assertEquals(1L, basket.items[0].menuItemId)
                assertEquals(2, basket.items[0].quantity)
            }
        }

        testSuite("POST /basket/items") {
            testBasketService("adds item to empty basket") { module ->
                module.menuClient.addMenuItem(
                    MenuItem(
                        id = 1L,
                        name = "Pizza Margherita",
                        description = "Classic tomato and mozzarella",
                        imageUrl = "https://example.com/pizza.jpg",
                        price = BigDecimal("12.99"),
                        stock = 100,
                    )
                )

                val response =
                    jsonClient().post("/basket/items") {
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

            testBasketService("increments quantity when item already in basket") { module ->
                module.menuClient.addMenuItem(
                    MenuItem(
                        id = 1L,
                        name = "Pizza",
                        description = "Delicious",
                        imageUrl = "https://example.com/pizza.jpg",
                        price = BigDecimal("10.00"),
                        stock = 100,
                    )
                )

                // Add item first time
                jsonClient().post("/basket/items") {
                    bearerAuth(testToken)
                    contentType(ContentType.Application.Json)
                    setBody(AddItemRequest(menuItemId = 1L, quantity = 2))
                }

                // Add same item again
                val response =
                    jsonClient().post("/basket/items") {
                        bearerAuth(testToken)
                        contentType(ContentType.Application.Json)
                        setBody(AddItemRequest(menuItemId = 1L, quantity = 3))
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                val basket = response.body<CustomerBasket>()
                assertEquals(1, basket.items.size)
                assertEquals(5, basket.items[0].quantity) // 2 + 3 = 5
            }

            testBasketService("returns 404 when menu item does not exist") { _ ->
                val response =
                    jsonClient().post("/basket/items") {
                        bearerAuth(testToken)
                        contentType(ContentType.Application.Json)
                        setBody(AddItemRequest(menuItemId = 999L, quantity = 1))
                    }

                assertEquals(HttpStatusCode.NotFound, response.status)
            }

            testBasketService("returns 400 for invalid quantity (zero)") { _ ->
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

            testBasketService("returns 400 for negative quantity") { _ ->
                val response =
                    jsonClient().post("/basket/items") {
                        bearerAuth(testToken)
                        contentType(ContentType.Application.Json)
                        setBody(AddItemRequest(menuItemId = 1L, quantity = -5))
                    }

                assertEquals(HttpStatusCode.BadRequest, response.status)
            }

            testBasketService("returns 401 when not authenticated") { _ ->
                val response =
                    jsonClient().post("/basket/items") {
                        contentType(ContentType.Application.Json)
                        setBody(AddItemRequest(menuItemId = 1L, quantity = 1))
                    }

                assertEquals(HttpStatusCode.Unauthorized, response.status)
            }
        }

        testSuite("PUT /basket/items/{itemId}") {
            testBasketService("updates item quantity") { module ->
                module.menuClient.addMenuItem(
                    MenuItem(
                        id = 1L,
                        name = "Pizza",
                        description = "Delicious",
                        imageUrl = "https://example.com/pizza.jpg",
                        price = BigDecimal("10.00"),
                        stock = 100,
                    )
                )

                // First add an item
                val addResponse =
                    jsonClient().post("/basket/items") {
                        bearerAuth(testToken)
                        contentType(ContentType.Application.Json)
                        setBody(AddItemRequest(menuItemId = 1L, quantity = 1))
                    }
                val addedBasket = addResponse.body<CustomerBasket>()
                val itemId = addedBasket.items[0].id

                // Update quantity
                val response =
                    jsonClient().put("/basket/items/$itemId") {
                        bearerAuth(testToken)
                        contentType(ContentType.Application.Json)
                        setBody(UpdateItemQuantityRequest(quantity = 10))
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                val basket = response.body<CustomerBasket>()
                assertEquals(1, basket.items.size)
                assertEquals(10, basket.items[0].quantity)
            }

            testBasketService("returns 404 when item not in basket") { _ ->
                val response =
                    jsonClient().put("/basket/items/non-existent-id") {
                        bearerAuth(testToken)
                        contentType(ContentType.Application.Json)
                        setBody(UpdateItemQuantityRequest(quantity = 5))
                    }

                assertEquals(HttpStatusCode.NotFound, response.status)
            }

            testBasketService("returns 400 for invalid quantity") { _ ->
                val response =
                    jsonClient().put("/basket/items/some-id") {
                        bearerAuth(testToken)
                        contentType(ContentType.Application.Json)
                        setBody(UpdateItemQuantityRequest(quantity = 0))
                    }

                assertEquals(HttpStatusCode.BadRequest, response.status)
            }

            testBasketService("returns 401 when not authenticated") { _ ->
                val response =
                    jsonClient().put("/basket/items/some-id") {
                        contentType(ContentType.Application.Json)
                        setBody(UpdateItemQuantityRequest(quantity = 5))
                    }

                assertEquals(HttpStatusCode.Unauthorized, response.status)
            }
        }

        testSuite("DELETE /basket/items/{itemId}") {
            testBasketService("removes item from basket") { module ->
                module.menuClient.addMenuItem(
                    MenuItem(
                        id = 1L,
                        name = "Pizza",
                        description = "Delicious",
                        imageUrl = "https://example.com/pizza.jpg",
                        price = BigDecimal("10.00"),
                        stock = 100,
                    )
                )
                module.menuClient.addMenuItem(
                    MenuItem(
                        id = 2L,
                        name = "Pasta",
                        description = "Creamy",
                        imageUrl = "https://example.com/pasta.jpg",
                        price = BigDecimal("8.50"),
                        stock = 100,
                    )
                )

                // Add two items
                jsonClient().post("/basket/items") {
                    bearerAuth(testToken)
                    contentType(ContentType.Application.Json)
                    setBody(AddItemRequest(menuItemId = 1L, quantity = 1))
                }
                val basketWith2 =
                    jsonClient()
                        .post("/basket/items") {
                            bearerAuth(testToken)
                            contentType(ContentType.Application.Json)
                            setBody(AddItemRequest(menuItemId = 2L, quantity = 2))
                        }
                        .body<CustomerBasket>()

                val itemToRemove = basketWith2.items[0].id

                // Remove first item
                val response =
                    jsonClient().delete("/basket/items/$itemToRemove") { bearerAuth(testToken) }

                assertEquals(HttpStatusCode.OK, response.status)
                val basket = response.body<CustomerBasket>()
                assertEquals(1, basket.items.size)
                assertEquals(2L, basket.items[0].menuItemId)
            }

            testBasketService("returns 404 when item not in basket") { _ ->
                val response =
                    jsonClient().delete("/basket/items/non-existent-id") { bearerAuth(testToken) }

                assertEquals(HttpStatusCode.NotFound, response.status)
            }

            testBasketService("returns 401 when not authenticated") { _ ->
                val response = jsonClient().delete("/basket/items/some-id")
                assertEquals(HttpStatusCode.Unauthorized, response.status)
            }
        }

        testSuite("DELETE /basket") {
            testBasketService("clears entire basket") { module ->
                module.menuClient.addMenuItem(
                    MenuItem(
                        id = 1L,
                        name = "Pizza",
                        description = "Delicious",
                        imageUrl = "https://example.com/pizza.jpg",
                        price = BigDecimal("10.00"),
                        stock = 100,
                    )
                )

                // Add an item first
                jsonClient().post("/basket/items") {
                    bearerAuth(testToken)
                    contentType(ContentType.Application.Json)
                    setBody(AddItemRequest(menuItemId = 1L, quantity = 3))
                }

                // Clear basket
                val response = jsonClient().delete("/basket") { bearerAuth(testToken) }

                assertEquals(HttpStatusCode.NoContent, response.status)

                // Verify basket is empty
                val getResponse = jsonClient().get("/basket") { bearerAuth(testToken) }
                val basket = getResponse.body<CustomerBasket>()
                assertTrue(basket.items.isEmpty())
            }

            testBasketService("returns 204 even when basket is already empty") { _ ->
                val response = jsonClient().delete("/basket") { bearerAuth(testToken) }

                assertEquals(HttpStatusCode.NoContent, response.status)
            }

            testBasketService("returns 401 when not authenticated") { _ ->
                val response = jsonClient().delete("/basket")
                assertEquals(HttpStatusCode.Unauthorized, response.status)
            }
        }

        testSuite("User isolation") {
            testBasketService("different users have separate baskets") { module ->
                module.menuClient.addMenuItem(
                    MenuItem(
                        id = 1L,
                        name = "Pizza",
                        description = "Delicious",
                        imageUrl = "https://example.com/pizza.jpg",
                        price = BigDecimal("10.00"),
                        stock = 100,
                    )
                )

                val user1Token = createUserToken(userId = "user-1")
                val user2Token = createUserToken(userId = "user-2")

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
                val basket1 =
                    jsonClient().get("/basket") { bearerAuth(user1Token) }.body<CustomerBasket>()
                assertEquals("user-1", basket1.buyerId)
                assertEquals(5, basket1.items[0].quantity)

                // Verify user 2's basket
                val basket2 =
                    jsonClient().get("/basket") { bearerAuth(user2Token) }.body<CustomerBasket>()
                assertEquals("user-2", basket2.buyerId)
                assertEquals(2, basket2.items[0].quantity)
            }
        }
    }

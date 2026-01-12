package io.ktor.foodies.basket

import com.redis.testcontainers.RedisContainer
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.aroundEachTest
import de.infix.testBalloon.framework.core.testSuite
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.coroutines
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalLettuceCoroutinesApi::class)
val redisBasketRepositorySpec by testSuite {
    val container = testFixture {
        RedisContainer("redis:7-alpine").apply { start() }
    }

    val redisClient = testFixture {
        RedisClient.create(container().redisURI)
    }

    val repository = testFixture {
        val connection = redisClient().connect()
        RedisBasketRepository(connection.coroutines())
    }

    testSuite(
        "tests",
        testConfig = TestConfig.aroundEachTest { test ->
            // Clean up Redis before each test
            val connection = redisClient().connect()
            connection.sync().flushall()
            connection.close()
            test()
        }
    ) {
        test("getBasket returns null when basket does not exist") {
            val result = repository().getBasket("non-existent-user")

            assertNull(result)
        }

        test("updateBasket stores and retrieves basket correctly") {
            val basket = CustomerBasket(
                buyerId = "user-123",
                items = listOf(
                    BasketItem(
                        id = "item-1",
                        menuItemId = 1L,
                        menuItemName = "Pizza",
                        menuItemDescription = "Delicious pizza",
                        menuItemImageUrl = "https://example.com/pizza.jpg",
                        unitPrice = BigDecimal("12.99"),
                        quantity = 2
                    )
                )
            )

            val saved = repository().updateBasket(basket)
            val retrieved = repository().getBasket("user-123")

            assertEquals(basket, saved)
            assertNotNull(retrieved)
            assertEquals(basket.buyerId, retrieved.buyerId)
            assertEquals(basket.items.size, retrieved.items.size)
            assertEquals(basket.items[0].id, retrieved.items[0].id)
            assertEquals(basket.items[0].menuItemId, retrieved.items[0].menuItemId)
            assertEquals(basket.items[0].menuItemName, retrieved.items[0].menuItemName)
            assertEquals(basket.items[0].unitPrice, retrieved.items[0].unitPrice)
            assertEquals(basket.items[0].quantity, retrieved.items[0].quantity)
        }

        test("updateBasket overwrites existing basket") {
            val originalBasket = CustomerBasket(
                buyerId = "user-123",
                items = listOf(
                    BasketItem(
                        id = "item-1",
                        menuItemId = 1L,
                        menuItemName = "Pizza",
                        menuItemDescription = "Delicious",
                        menuItemImageUrl = "https://example.com/pizza.jpg",
                        unitPrice = BigDecimal("10.00"),
                        quantity = 1
                    )
                )
            )
            val updatedBasket = CustomerBasket(
                buyerId = "user-123",
                items = listOf(
                    BasketItem(
                        id = "item-1",
                        menuItemId = 1L,
                        menuItemName = "Pizza",
                        menuItemDescription = "Delicious",
                        menuItemImageUrl = "https://example.com/pizza.jpg",
                        unitPrice = BigDecimal("10.00"),
                        quantity = 5
                    ),
                    BasketItem(
                        id = "item-2",
                        menuItemId = 2L,
                        menuItemName = "Pasta",
                        menuItemDescription = "Creamy",
                        menuItemImageUrl = "https://example.com/pasta.jpg",
                        unitPrice = BigDecimal("8.50"),
                        quantity = 2
                    )
                )
            )

            repository().updateBasket(originalBasket)
            repository().updateBasket(updatedBasket)
            val retrieved = repository().getBasket("user-123")

            assertNotNull(retrieved)
            assertEquals(2, retrieved.items.size)
            assertEquals(5, retrieved.items[0].quantity)
        }

        test("deleteBasket removes basket and returns true") {
            val basket = CustomerBasket(
                buyerId = "user-123",
                items = listOf(
                    BasketItem(
                        id = "item-1",
                        menuItemId = 1L,
                        menuItemName = "Pizza",
                        menuItemDescription = "Delicious",
                        menuItemImageUrl = "https://example.com/pizza.jpg",
                        unitPrice = BigDecimal("10.00"),
                        quantity = 1
                    )
                )
            )
            repository().updateBasket(basket)

            val deleted = repository().deleteBasket("user-123")
            val retrieved = repository().getBasket("user-123")

            assertTrue(deleted)
            assertNull(retrieved)
        }

        test("deleteBasket returns false when basket does not exist") {
            val deleted = repository().deleteBasket("non-existent-user")

            assertFalse(deleted)
        }

        test("baskets are isolated by buyerId") {
            val basket1 = CustomerBasket(
                buyerId = "user-1",
                items = listOf(
                    BasketItem(
                        id = "item-1",
                        menuItemId = 1L,
                        menuItemName = "Pizza",
                        menuItemDescription = "Delicious",
                        menuItemImageUrl = "https://example.com/pizza.jpg",
                        unitPrice = BigDecimal("10.00"),
                        quantity = 1
                    )
                )
            )
            val basket2 = CustomerBasket(
                buyerId = "user-2",
                items = listOf(
                    BasketItem(
                        id = "item-2",
                        menuItemId = 2L,
                        menuItemName = "Pasta",
                        menuItemDescription = "Creamy",
                        menuItemImageUrl = "https://example.com/pasta.jpg",
                        unitPrice = BigDecimal("8.50"),
                        quantity = 3
                    )
                )
            )

            repository().updateBasket(basket1)
            repository().updateBasket(basket2)

            val retrieved1 = repository().getBasket("user-1")
            val retrieved2 = repository().getBasket("user-2")

            assertNotNull(retrieved1)
            assertNotNull(retrieved2)
            assertEquals("user-1", retrieved1.buyerId)
            assertEquals("user-2", retrieved2.buyerId)
            assertEquals(1L, retrieved1.items[0].menuItemId)
            assertEquals(2L, retrieved2.items[0].menuItemId)
        }

        test("empty basket can be stored and retrieved") {
            val emptyBasket = CustomerBasket(buyerId = "user-123", items = emptyList())

            repository().updateBasket(emptyBasket)
            val retrieved = repository().getBasket("user-123")

            assertNotNull(retrieved)
            assertEquals("user-123", retrieved.buyerId)
            assertTrue(retrieved.items.isEmpty())
        }
    }
}

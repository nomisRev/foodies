package io.ktor.foodies.basket

import de.infix.testBalloon.framework.core.TestExecutionScope
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.shared.TestRegistering
import io.ktor.foodies.server.test.ctxSuite
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestRegistering
context(ctx: ServiceContext)
fun TestSuite.testRedis(
    name: String,
    block: suspend context(TestExecutionScope) (repository: RedisBasketRepository) -> Unit
) = test(name) {
    ctx.redisClient().connect().use { connection ->
        block(RedisBasketRepository(connection.coroutines()))
        connection.sync().flushall()
    }
}

@OptIn(ExperimentalLettuceCoroutinesApi::class)
val redisBasketRepositorySpec by ctxSuite(context = { serviceContext() }) {
    testRedis("getBasket returns null when basket does not exist") { repository ->
        val result = repository.getBasket("non-existent-user")

        assertNull(result)
    }

    testRedis("updateBasket stores and retrieves basket correctly") { repository ->
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

        val saved = repository.updateBasket(basket)
        val retrieved = repository.getBasket("user-123")

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

    testRedis("updateBasket overwrites existing basket") { repository ->
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

        repository.updateBasket(originalBasket)
        repository.updateBasket(updatedBasket)
        val retrieved = repository.getBasket("user-123")

        assertNotNull(retrieved)
        assertEquals(2, retrieved.items.size)
        assertEquals(5, retrieved.items[0].quantity)
    }

    testRedis("deleteBasket removes basket and returns true") { repository ->
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
        repository.updateBasket(basket)

        val deleted = repository.deleteBasket("user-123")
        val retrieved = repository.getBasket("user-123")

        assertTrue(deleted)
        assertNull(retrieved)
    }

    testRedis("deleteBasket returns false when basket does not exist") { repository ->
        val deleted = repository.deleteBasket("non-existent-user")

        assertFalse(deleted)
    }

    testRedis("baskets are isolated by buyerId") { repository ->
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

        repository.updateBasket(basket1)
        repository.updateBasket(basket2)

        val retrieved1 = repository.getBasket("user-1")
        val retrieved2 = repository.getBasket("user-2")

        assertNotNull(retrieved1)
        assertNotNull(retrieved2)
        assertEquals("user-1", retrieved1.buyerId)
        assertEquals("user-2", retrieved2.buyerId)
        assertEquals(1L, retrieved1.items[0].menuItemId)
        assertEquals(2L, retrieved2.items[0].menuItemId)
    }

    testRedis("empty basket can be stored and retrieved") { repository ->
        val emptyBasket = CustomerBasket(buyerId = "user-123", items = emptyList())

        repository.updateBasket(emptyBasket)
        val retrieved = repository.getBasket("user-123")

        assertNotNull(retrieved)
        assertEquals("user-123", retrieved.buyerId)
        assertTrue(retrieved.items.isEmpty())
    }
}

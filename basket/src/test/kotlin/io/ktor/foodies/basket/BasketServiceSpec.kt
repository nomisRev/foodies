package io.ktor.foodies.basket

import de.infix.testBalloon.framework.core.testSuite
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Holds test dependencies for the BasketService.
 */
private data class TestContext(
    val menuClient: InMemoryMenuClient,
    val repository: InMemoryBasketRepository,
    val service: BasketService
)

private fun createTestContext(): TestContext {
    val menuClient = InMemoryMenuClient()
    val repository = InMemoryBasketRepository()
    val service = BasketServiceImpl(repository, menuClient)
    return TestContext(menuClient, repository, service)
}

val basketServiceSpec by testSuite {
    test("getBasket returns empty basket when no basket exists") {
        val ctx = createTestContext()
        val basket = ctx.service.getBasket("user-123")

        assertEquals("user-123", basket.buyerId)
        assertTrue(basket.items.isEmpty())
    }

    test("addItem creates new basket with item when basket does not exist") {
        val ctx = createTestContext()
        ctx.menuClient.addMenuItem(
            MenuItem(
                id = 1L,
                name = "Pizza Margherita",
                description = "Classic tomato and mozzarella",
                imageUrl = "https://example.com/pizza.jpg",
                price = BigDecimal("12.99")
            )
        )

        val basket = ctx.service.addItem(
            buyerId = "user-123",
            request = ValidatedAddItem(menuItemId = 1L, quantity = 2)
        )

        assertNotNull(basket)
        assertEquals("user-123", basket.buyerId)
        assertEquals(1, basket.items.size)
        assertEquals(1L, basket.items[0].menuItemId)
        assertEquals("Pizza Margherita", basket.items[0].menuItemName)
        assertEquals(2, basket.items[0].quantity)
        assertEquals(BigDecimal("12.99"), basket.items[0].unitPrice)
    }

    test("addItem increments quantity when item already exists in basket") {
        val ctx = createTestContext()
        ctx.menuClient.addMenuItem(
            MenuItem(
                id = 1L,
                name = "Pizza",
                description = "Delicious",
                imageUrl = "https://example.com/pizza.jpg",
                price = BigDecimal("10.00")
            )
        )

        ctx.service.addItem("user-123", ValidatedAddItem(menuItemId = 1L, quantity = 2))
        val basket = ctx.service.addItem("user-123", ValidatedAddItem(menuItemId = 1L, quantity = 3))

        assertNotNull(basket)
        assertEquals(1, basket.items.size)
        assertEquals(5, basket.items[0].quantity)
    }

    test("addItem adds new item to existing basket with different items") {
        val ctx = createTestContext()
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

        ctx.service.addItem("user-123", ValidatedAddItem(menuItemId = 1L, quantity = 1))
        val basket = ctx.service.addItem("user-123", ValidatedAddItem(menuItemId = 2L, quantity = 2))

        assertNotNull(basket)
        assertEquals(2, basket.items.size)
        assertEquals(1L, basket.items[0].menuItemId)
        assertEquals(1, basket.items[0].quantity)
        assertEquals(2L, basket.items[1].menuItemId)
        assertEquals(2, basket.items[1].quantity)
    }

    test("addItem returns null when menu item does not exist") {
        val ctx = createTestContext()
        val basket = ctx.service.addItem("user-123", ValidatedAddItem(menuItemId = 999L, quantity = 1))

        assertNull(basket)
    }

    test("updateItemQuantity updates quantity of existing item") {
        val ctx = createTestContext()
        ctx.menuClient.addMenuItem(
            MenuItem(
                id = 1L,
                name = "Pizza",
                description = "Delicious",
                imageUrl = "https://example.com/pizza.jpg",
                price = BigDecimal("10.00")
            )
        )
        val initialBasket = ctx.service.addItem("user-123", ValidatedAddItem(menuItemId = 1L, quantity = 1))
        assertNotNull(initialBasket)
        val itemId = initialBasket.items[0].id

        val basket = ctx.service.updateItemQuantity("user-123", itemId, ValidatedUpdateQuantity(quantity = 5))

        assertNotNull(basket)
        assertEquals(1, basket.items.size)
        assertEquals(5, basket.items[0].quantity)
    }

    test("updateItemQuantity returns null when basket does not exist") {
        val ctx = createTestContext()
        val basket = ctx.service.updateItemQuantity("user-123", "non-existent-item", ValidatedUpdateQuantity(quantity = 5))

        assertNull(basket)
    }

    test("updateItemQuantity returns null when item not in basket") {
        val ctx = createTestContext()
        ctx.menuClient.addMenuItem(
            MenuItem(
                id = 1L,
                name = "Pizza",
                description = "Delicious",
                imageUrl = "https://example.com/pizza.jpg",
                price = BigDecimal("10.00")
            )
        )
        ctx.service.addItem("user-123", ValidatedAddItem(menuItemId = 1L, quantity = 1))

        val basket = ctx.service.updateItemQuantity("user-123", "wrong-item-id", ValidatedUpdateQuantity(quantity = 5))

        assertNull(basket)
    }

    test("removeItem removes item from basket") {
        val ctx = createTestContext()
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
        ctx.service.addItem("user-123", ValidatedAddItem(menuItemId = 1L, quantity = 1))
        val basketWithTwo = ctx.service.addItem("user-123", ValidatedAddItem(menuItemId = 2L, quantity = 2))
        assertNotNull(basketWithTwo)
        val itemToRemove = basketWithTwo.items[0].id

        val basket = ctx.service.removeItem("user-123", itemToRemove)

        assertNotNull(basket)
        assertEquals(1, basket.items.size)
        assertEquals(2L, basket.items[0].menuItemId)
    }

    test("removeItem returns null when basket does not exist") {
        val ctx = createTestContext()
        val basket = ctx.service.removeItem("user-123", "non-existent-item")

        assertNull(basket)
    }

    test("removeItem returns null when item not in basket") {
        val ctx = createTestContext()
        ctx.menuClient.addMenuItem(
            MenuItem(
                id = 1L,
                name = "Pizza",
                description = "Delicious",
                imageUrl = "https://example.com/pizza.jpg",
                price = BigDecimal("10.00")
            )
        )
        ctx.service.addItem("user-123", ValidatedAddItem(menuItemId = 1L, quantity = 1))

        val basket = ctx.service.removeItem("user-123", "wrong-item-id")

        assertNull(basket)
    }

    test("clearBasket removes all items") {
        val ctx = createTestContext()
        ctx.menuClient.addMenuItem(
            MenuItem(
                id = 1L,
                name = "Pizza",
                description = "Delicious",
                imageUrl = "https://example.com/pizza.jpg",
                price = BigDecimal("10.00")
            )
        )
        ctx.service.addItem("user-123", ValidatedAddItem(menuItemId = 1L, quantity = 3))

        ctx.service.clearBasket("user-123")
        val basket = ctx.service.getBasket("user-123")

        assertTrue(basket.items.isEmpty())
    }

    test("clearBasket succeeds even when basket does not exist") {
        val ctx = createTestContext()
        ctx.service.clearBasket("non-existent-user")
        // Should not throw
    }

    test("totalPrice calculates correctly for multiple items") {
        val ctx = createTestContext()
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

        ctx.service.addItem("user-123", ValidatedAddItem(menuItemId = 1L, quantity = 2)) // 20.00
        val basket = ctx.service.addItem("user-123", ValidatedAddItem(menuItemId = 2L, quantity = 3)) // 25.50

        assertNotNull(basket)
        assertEquals(BigDecimal("45.50"), basket.totalPrice())
    }
}

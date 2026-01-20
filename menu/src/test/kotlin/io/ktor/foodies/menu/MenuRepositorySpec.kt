package io.ktor.foodies.menu

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.aroundEachTest
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.events.order.StockValidationItem
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

val menuRepositorySpec by testSuite {
    val dataSource = migratedMenuDataSource()
    val repository = testFixture { ExposedMenuRepository(dataSource().database) }

    testSuite(
        "tests",
        testConfig = TestConfig.aroundEachTest { test ->
            transaction(dataSource().database) { MenuItemsTable.deleteAll() }
            test()
        }) {
        test("create stores a menu item and findById retrieves it") {
            val created = repository().create(
                CreateMenuItem(
                    name = "Margherita",
                    description = "Classic pizza",
                    imageUrl = "https://example.com/margherita.jpg",
                    price = BigDecimal("9.50"),
                    stock = 10,
                )
            )

            val found = repository().findById(created.id)

            assertEquals(created, found)
            assertEquals(10, found?.stock)
        }

        test("list returns items ordered by id with pagination") {
            val first = repository().create(
                CreateMenuItem(
                    name = "First",
                    description = "First item",
                    imageUrl = "https://example.com/first.jpg",
                    price = BigDecimal("5.00"),
                    stock = 5,
                )
            )
            val second = repository().create(
                CreateMenuItem(
                    name = "Second",
                    description = "Second item",
                    imageUrl = "https://example.com/second.jpg",
                    price = BigDecimal("6.00"),
                    stock = 5,
                )
            )
            val third = repository().create(
                CreateMenuItem(
                    name = "Third",
                    description = "Third item",
                    imageUrl = "https://example.com/third.jpg",
                    price = BigDecimal("7.00"),
                    stock = 5,
                )
            )

            assertEquals(listOf(first, second), repository().list(offset = 0, limit = 2))
            assertEquals(listOf(second, third), repository().list(offset = 1, limit = 2))
            assertTrue(repository().list(offset = 5, limit = 2).isEmpty())
        }

        test("update modifies existing menu item and returns updated version") {
            val created = repository().create(
                CreateMenuItem(
                    name = "Pasta",
                    description = "Creamy pasta",
                    imageUrl = "https://example.com/pasta.jpg",
                    price = BigDecimal("12.00"),
                    stock = 20,
                )
            )

            val updated = repository().update(
                created.id,
                UpdateMenuItem(
                    name = "Updated Pasta",
                    price = BigDecimal("13.25"),
                    stock = 15,
                )
            )

            assertNotNull(updated)
            assertEquals("Updated Pasta", updated.name)
            assertEquals(BigDecimal("13.25"), updated.price)
            assertEquals(15, updated.stock)
            assertEquals(updated, repository().findById(created.id))
        }

        test("update returns null when menu item does not exist") {
            val updated = repository().update(
                id = 9999L,
                request = UpdateMenuItem(name = "Missing"),
            )

            assertNull(updated)
        }

        test("delete removes existing items and signals absence") {
            val created = repository().create(
                CreateMenuItem(
                    name = "Burger",
                    description = "Juicy burger",
                    imageUrl = "https://example.com/burger.jpg",
                    price = BigDecimal("10.00"),
                    stock = 10,
                )
            )

            val deleted = repository().delete(created.id)
            val missing = repository().findById(created.id)
            val secondDelete = repository().delete(created.id)

            assertTrue(deleted)
            assertNull(missing)
            assertEquals(false, secondDelete)
        }

        test("validateAndReserveStock reserves stock when available") {
            val pizza = repository().create(
                CreateMenuItem("Pizza", "Good", "url", BigDecimal("10.00"), 10)
            )

            val result = repository().validateAndReserveStock(
                listOf(StockValidationItem(pizza.id, 3))
            )

            assertTrue(result is StockValidationResult.Success)
            assertEquals(7, repository().findById(pizza.id)?.stock)
        }

        test("validateAndReserveStock fails and does not reserve when stock is insufficient") {
            val pizza = repository().create(
                CreateMenuItem("Pizza", "Good", "url", BigDecimal("10.00"), 2)
            )

            val result = repository().validateAndReserveStock(
                listOf(StockValidationItem(pizza.id, 3))
            )

            assertTrue(result is StockValidationResult.Failure)
            assertEquals(1, result.rejectedItems.size)
            assertEquals(pizza.id, result.rejectedItems[0].menuItemId)
            assertEquals(2, result.rejectedItems[0].availableQuantity)
            assertEquals(2, repository().findById(pizza.id)?.stock)
        }

        test("returnStock increases stock level") {
            val pizza = repository().create(
                CreateMenuItem("Pizza", "Good", "url", BigDecimal("10.00"), 5)
            )

            repository().returnStock(listOf(StockValidationItem(pizza.id, 3)))

            assertEquals(8, repository().findById(pizza.id)?.stock)
        }
    }
}
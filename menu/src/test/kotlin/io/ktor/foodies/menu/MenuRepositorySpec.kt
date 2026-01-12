package io.ktor.foodies.menu

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.aroundEachTest
import de.infix.testBalloon.framework.core.testSuite
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
            test()
            transaction(dataSource().database) { MenuItemsTable.deleteAll() }
        }) {
        test("create stores a menu item and findById retrieves it") {
            val created = repository().create(
                CreateMenuItem(
                    name = "Margherita",
                    description = "Classic pizza",
                    imageUrl = "https://example.com/margherita.jpg",
                    price = BigDecimal("9.50"),
                    categoryId = 1,
                )
            )

            val found = repository().findById(created.id)

            assertEquals(created, found)
        }

        test("list returns items ordered by id with pagination") {
            val first = repository().create(
                CreateMenuItem(
                    name = "First",
                    description = "First item",
                    imageUrl = "https://example.com/first.jpg",
                    price = BigDecimal("5.00"),
                    categoryId = 1,
                )
            )
            val second = repository().create(
                CreateMenuItem(
                    name = "Second",
                    description = "Second item",
                    imageUrl = "https://example.com/second.jpg",
                    price = BigDecimal("6.00"),
                    categoryId = 1,
                )
            )
            val third = repository().create(
                CreateMenuItem(
                    name = "Third",
                    description = "Third item",
                    imageUrl = "https://example.com/third.jpg",
                    price = BigDecimal("7.00"),
                    categoryId = 1,
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
                    categoryId = 1,
                )
            )

            val updated = repository().update(
                created.id,
                UpdateMenuItem(
                    name = "Updated Pasta",
                    price = BigDecimal("13.25"),
                    categoryId = 2,
                )
            )

            assertNotNull(updated)
            assertEquals("Updated Pasta", updated.name)
            assertEquals(BigDecimal("13.25"), updated.price)
            assertEquals(2, updated.categoryId)
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
                    categoryId = 1,
                )
            )

            val deleted = repository().delete(created.id)
            val missing = repository().findById(created.id)
            val secondDelete = repository().delete(created.id)

            assertTrue(deleted)
            assertNull(missing)
            assertEquals(false, secondDelete)
        }

        test("search finds items by name or description (case insensitive)") {
            val pizza = repository().create(
                CreateMenuItem(
                    name = "Margherita Pizza",
                    description = "Classic tomato and mozzarella",
                    imageUrl = "https://example.com/margherita.jpg",
                    price = BigDecimal("9.50"),
                    categoryId = 1,
                )
            )
            val pasta = repository().create(
                CreateMenuItem(
                    name = "Carbonara Pasta",
                    description = "Creamy sauce with bacon",
                    imageUrl = "https://example.com/carbonara.jpg",
                    price = BigDecimal("12.00"),
                    categoryId = 1,
                )
            )

            // Search by name
            assertEquals(listOf(pizza), repository().search("Pizza"))
            assertEquals(listOf(pasta), repository().search("pasta")) // case insensitive

            // Search by description
            assertEquals(listOf(pizza), repository().search("tomato"))
            assertEquals(listOf(pasta), repository().search("CREAMY"))

            // Search matches both
            val all = repository().search("a")
            assertTrue(all.contains(pizza))
            assertTrue(all.contains(pasta))

            // No matches
            assertTrue(repository().search("Burger").isEmpty())
        }
    }
}
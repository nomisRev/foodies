package io.ktor.foodies.menu

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.aroundEachTest
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.server.ValidationException
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

val menuServiceSpec by testSuite {
    val dataSource = migratedMenuDataSource()
    val repository = testFixture { ExposedMenuRepository(dataSource().database) }
    val service = testFixture { MenuServiceImpl(repository()) }

    testSuite(
        "tests",
        testConfig = TestConfig.aroundEachTest { test ->
            test()
            transaction(dataSource().database) { MenuItemsTable.deleteAll() }
        }) {
        test("list applies defaults and clamps pagination") {
            val created = (0 until 60).map {
                service().create(
                    CreateMenuItemRequest(
                        name = "Item $it",
                        description = "Description $it",
                        imageUrl = "https://example.com/$it.jpg",
                        price = BigDecimal("1.00"),
                        categoryId = 1,
                    )
                )
            }

            val defaultList = service().list()
            val clampedList = service().list(limit = 100)
            val negativeOffset = service().list(offset = -5, limit = 5)
            val pastEnd = service().list(offset = 1000, limit = 10)

            assertEquals(created.take(20).map { it.id }, defaultList.map { it.id })
            assertEquals(50, clampedList.size)
            assertEquals(created.take(5).map { it.id }, negativeOffset.map { it.id })
            assertTrue(pastEnd.isEmpty())
        }

        test("create validates payload and update/delete respect existence") {
            val validationError = assertFailsWith<ValidationException> {
                service().create(
                    CreateMenuItemRequest(
                        name = " ",
                        description = "",
                        imageUrl = " ",
                        price = BigDecimal.ZERO,
                        categoryId = 1,
                    )
                )
            }
            // Kova collects all validation errors
            assertTrue(validationError.reasons.size >= 4, "Expected at least 4 validation errors")
            assertTrue(validationError.reasons.any { it.contains("blank") || it.contains("not be empty") })
            assertTrue(validationError.reasons.any { it.contains("greater") || it.contains("must be more than") })

            val created = service().create(
                CreateMenuItemRequest(
                    name = "Burger",
                    description = "Juicy",
                    imageUrl = "https://example.com/burger.jpg",
                    price = BigDecimal("11.50"),
                    categoryId = 1,
                )
            )

            val updated = service().update(
                created.id,
                UpdateMenuItemRequest(
                    name = "Updated Burger",
                    price = BigDecimal("12.00"),
                )
            )

            assertNotNull(updated)
            assertEquals("Updated Burger", updated.name)
            assertEquals(BigDecimal("12.00"), updated.price)
            assertEquals(updated, repository().findById(created.id))

            assertTrue(service().delete(created.id))
            assertNull(service().get(created.id))
            assertNull(service().update(9999L, UpdateMenuItemRequest(name = "Missing")))
            assertEquals(false, service().delete(9999L))
        }
    }
}

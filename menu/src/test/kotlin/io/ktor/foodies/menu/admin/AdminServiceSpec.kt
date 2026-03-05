package io.ktor.foodies.menu.admin

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.aroundEachTest
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.menu.migratedMenuDataSource
import io.ktor.foodies.menu.persistence.MenuItemsTable
import io.ktor.foodies.server.ValidationException
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

val adminServiceSpec by testSuite {
    val dataSource = migratedMenuDataSource()
    val repository = testFixture { ExposedAdminRepository(dataSource().database) }
    val service = testFixture { AdminServiceImpl(repository()) }

    testSuite(
        "tests",
        testConfig = TestConfig.aroundEachTest { test ->
            transaction(dataSource().database) { MenuItemsTable.deleteAll() }
            test()
        }
    ) {
        test("create persists a valid item") {
            val created = service().create(
                CreateMenuItemRequest(
                    name = "Burger",
                    description = "Juicy beef burger",
                    imageUrl = "https://example.com/burger.jpg",
                    price = BigDecimal("11.50"),
                    stock = 10,
                )
            )

            assertNotNull(created.id)
            assertEquals("Burger", created.name)
            assertEquals(10, created.stock)
            assertEquals(created, repository().findById(created.id))
        }

        test("create rejects invalid payload") {
            val error = assertFailsWith<ValidationException> {
                service().create(
                    CreateMenuItemRequest(
                        name = " ",
                        description = "",
                        imageUrl = " ",
                        price = BigDecimal.ZERO,
                        stock = -1,
                    )
                )
            }

            assertTrue(error.reasons.size >= 5)
        }

        test("update modifies existing item") {
            val created = service().create(
                CreateMenuItemRequest(
                    name = "Pasta",
                    description = "Al dente",
                    imageUrl = "https://example.com/pasta.jpg",
                    price = BigDecimal("9.00"),
                    stock = 8,
                )
            )

            val updated = service().update(
                created.id,
                UpdateMenuItemRequest(name = "Penne", price = BigDecimal("10.00"), stock = 3)
            )

            assertNotNull(updated)
            assertEquals("Penne", updated.name)
            assertEquals(BigDecimal("10.00"), updated.price)
            assertEquals(3, updated.stock)
        }

        test("update returns null for missing item") {
            assertNull(service().update(9999L, UpdateMenuItemRequest(name = "Ghost")))
        }

        test("delete removes existing item") {
            val created = service().create(
                CreateMenuItemRequest(
                    name = "Salad",
                    description = "Fresh greens",
                    imageUrl = "https://example.com/salad.jpg",
                    price = BigDecimal("6.00"),
                    stock = 20,
                )
            )

            assertTrue(service().delete(created.id))
            assertNull(repository().findById(created.id))
        }

        test("delete returns false for missing item") {
            assertEquals(false, service().delete(9999L))
        }
    }
}

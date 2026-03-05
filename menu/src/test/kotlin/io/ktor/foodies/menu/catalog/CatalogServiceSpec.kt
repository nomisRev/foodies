package io.ktor.foodies.menu.catalog

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.aroundEachTest
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.menu.admin.CreateMenuItem
import io.ktor.foodies.menu.admin.ExposedAdminRepository
import io.ktor.foodies.menu.persistence.ExposedMenuRepository
import io.ktor.foodies.menu.migratedMenuDataSource
import io.ktor.foodies.menu.persistence.MenuItemsTable
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull

val catalogServiceSpec by testSuite {
    val dataSource = migratedMenuDataSource()
    val adminRepository = testFixture { ExposedAdminRepository(dataSource().database) }
    val repository = testFixture { ExposedMenuRepository(dataSource().database) }
    val service = testFixture { CatalogServiceImpl(repository()) }

    testSuite(
        "tests",
        testConfig = TestConfig.aroundEachTest { test ->
            transaction(dataSource().database) { MenuItemsTable.deleteAll() }
            test()
        }
    ) {
        test("list returns items with default pagination") {
            val created = (0 until 5).map {
                adminRepository().create(
                    CreateMenuItem(
                        name = "Item $it",
                        description = "Description $it",
                        imageUrl = "https://example.com/$it.jpg",
                        price = BigDecimal("1.00"),
                        stock = 10,
                    )
                )
            }

            val result = service().list()

            assertEquals(created.map { it.id }, result.map { it.id })
        }

        test("list clamps limit to max and coerces negative offset") {
            (0 until 60).forEach {
                adminRepository().create(
                    CreateMenuItem(
                        name = "Item $it",
                        description = "Description $it",
                        imageUrl = "https://example.com/$it.jpg",
                        price = BigDecimal("1.00"),
                        stock = 10,
                    )
                )
            }

            val clamped = service().list(limit = 100)
            val negativeOffset = service().list(offset = -5, limit = 5)

            assertEquals(50, clamped.size)
            assertEquals(5, negativeOffset.size)
        }

        test("get returns item when it exists") {
            val created = adminRepository().create(
                CreateMenuItem(
                    name = "Burger",
                    description = "Juicy",
                    imageUrl = "https://example.com/burger.jpg",
                    price = BigDecimal("11.50"),
                    stock = 5,
                )
            )

            val result = service().get(created.id)

            assertEquals(created, result)
        }

        test("get returns null when item does not exist") {
            assertNull(service().get(-1L))
        }
    }
}

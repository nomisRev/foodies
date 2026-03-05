package io.ktor.foodies.menu.stock

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.aroundEachTest
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.events.order.StockValidationItem
import io.ktor.foodies.menu.admin.CreateMenuItem
import io.ktor.foodies.menu.admin.ExposedAdminRepository
import io.ktor.foodies.menu.migratedMenuDataSource
import io.ktor.foodies.menu.persistence.MenuItemsTable
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

val stockServiceSpec by testSuite {
    val dataSource = migratedMenuDataSource()
    val adminRepository = testFixture { ExposedAdminRepository(dataSource().database) }
    val stockRepository = testFixture { ExposedStockRepository(dataSource().database) }
    val service = testFixture { StockServiceImpl(stockRepository()) }

    testSuite(
        "tests",
        testConfig = TestConfig.aroundEachTest { test ->
            transaction(dataSource().database) { MenuItemsTable.deleteAll() }
            test()
        }
    ) {
        test("validateAndReserveStock reserves stock when all items are available") {
            val pizza = adminRepository().create(
                CreateMenuItem(
                    name = "Pizza",
                    description = "Classic",
                    imageUrl = "https://example.com/pizza.jpg",
                    price = BigDecimal("10.00"),
                    stock = 10,
                )
            )

            val result = service().validateAndReserveStock(
                orderId = 1L,
                items = listOf(StockValidationItem(pizza.id, 3)),
            )

            assertTrue(result is StockValidationResult.Success)
            assertEquals(7, adminRepository().findById(pizza.id)?.stock)
        }

        test("validateAndReserveStock fails and does not partially reserve stock") {
            val pizza = adminRepository().create(
                CreateMenuItem(
                    name = "Pizza",
                    description = "Classic",
                    imageUrl = "https://example.com/pizza.jpg",
                    price = BigDecimal("10.00"),
                    stock = 10,
                )
            )
            val pasta = adminRepository().create(
                CreateMenuItem(
                    name = "Pasta",
                    description = "Fresh",
                    imageUrl = "https://example.com/pasta.jpg",
                    price = BigDecimal("12.00"),
                    stock = 1,
                )
            )

            val result = service().validateAndReserveStock(
                orderId = 2L,
                items = listOf(
                    StockValidationItem(pizza.id, 2),
                    StockValidationItem(pasta.id, 2),
                ),
            )

            assertTrue(result is StockValidationResult.Failure)
            assertEquals(1, result.rejectedItems.size)
            assertEquals(pasta.id, result.rejectedItems[0].menuItemId)
            assertEquals(1, result.rejectedItems[0].availableQuantity)
            assertEquals(10, adminRepository().findById(pizza.id)?.stock)
            assertEquals(1, adminRepository().findById(pasta.id)?.stock)
        }

        test("returnStock increases stock level") {
            val burger = adminRepository().create(
                CreateMenuItem(
                    name = "Burger",
                    description = "Juicy",
                    imageUrl = "https://example.com/burger.jpg",
                    price = BigDecimal("11.50"),
                    stock = 5,
                )
            )

            service().returnStock(
                orderId = 3L,
                items = listOf(StockValidationItem(burger.id, 3)),
            )

            assertEquals(8, adminRepository().findById(burger.id)?.stock)
        }
    }
}

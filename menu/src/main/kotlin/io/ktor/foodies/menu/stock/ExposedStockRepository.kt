package io.ktor.foodies.menu.stock

import io.ktor.foodies.events.menu.RejectedItem
import io.ktor.foodies.events.order.StockValidationItem
import io.ktor.foodies.menu.persistence.ExposedMenuRepository
import io.ktor.foodies.menu.persistence.MenuItemsTable
import io.ktor.foodies.menu.persistence.toMenuItem
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock

class ExposedStockRepository(database: Database) :
    ExposedMenuRepository(database), StockRepository {

    override fun validateAndReserveStock(items: List<StockValidationItem>): StockValidationResult = transaction(database) {
        val menuItems = MenuItemsTable.selectAll()
            .where { MenuItemsTable.id inList items.map { it.menuItemId } }
            .map { it.toMenuItem() }
            .associateBy { it.id }

        val rejectedItems = mutableListOf<RejectedItem>()

        for (item in items) {
            val menuItem = menuItems[item.menuItemId]
            if (menuItem == null || menuItem.stock < item.quantity) {
                rejectedItems.add(
                    RejectedItem(
                        menuItemId = item.menuItemId,
                        menuItemName = menuItem?.name ?: "Unknown",
                        requestedQuantity = item.quantity,
                        availableQuantity = menuItem?.stock ?: 0
                    )
                )
            }
        }

        if (rejectedItems.isNotEmpty()) {
            StockValidationResult.Failure(rejectedItems, Clock.System.now())
        } else {
            for (item in items) {
                MenuItemsTable.update({ MenuItemsTable.id eq item.menuItemId }) {
                    it[stock] = stock - item.quantity
                }
            }
            StockValidationResult.Success(Clock.System.now())
        }
    }

    override fun returnStock(items: List<StockValidationItem>): Unit = transaction(database) {
        for (item in items) {
            MenuItemsTable.update({ MenuItemsTable.id eq item.menuItemId }) {
                it[stock] = stock + item.quantity
            }
        }
    }
}

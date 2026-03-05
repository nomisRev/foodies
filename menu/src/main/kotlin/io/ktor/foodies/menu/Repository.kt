package io.ktor.foodies.menu

import io.ktor.foodies.events.menu.RejectedItem
import io.ktor.foodies.events.order.StockValidationItem
import io.ktor.foodies.menu.persistence.MenuItemsTable
import io.ktor.foodies.menu.persistence.toMenuItem
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock

interface MenuRepository : io.ktor.foodies.menu.persistence.MenuRepository {
    fun create(request: CreateMenuItem): MenuItem
    fun delete(id: Long): Boolean
    fun validateAndReserveStock(items: List<StockValidationItem>): StockValidationResult
    fun returnStock(items: List<StockValidationItem>)
}

class ExposedMenuRepository(database: Database) :
    io.ktor.foodies.menu.persistence.ExposedMenuRepository(database), MenuRepository {

    override fun create(request: CreateMenuItem): MenuItem = transaction(database) {
        val returning = MenuItemsTable.insertReturning(
            listOf(
                MenuItemsTable.id,
                MenuItemsTable.createdAt,
                MenuItemsTable.updatedAt
            ),
        ) { row ->
            row[name] = request.name
            row[description] = request.description
            row[imageUrl] = request.imageUrl
            row[price] = request.price
            row[stock] = request.stock
        }.single()

        MenuItem(
            id = returning[MenuItemsTable.id].value,
            name = request.name,
            description = request.description,
            imageUrl = request.imageUrl,
            price = request.price,
            stock = request.stock,
            createdAt = returning[MenuItemsTable.createdAt],
            updatedAt = returning[MenuItemsTable.updatedAt],
        )
    }

    override fun delete(id: Long): Boolean = transaction(database) {
        MenuItemsTable.deleteWhere { MenuItemsTable.id eq id } == 1
    }

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

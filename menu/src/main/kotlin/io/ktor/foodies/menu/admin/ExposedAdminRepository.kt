package io.ktor.foodies.menu.admin

import io.ktor.foodies.menu.MenuItem
import io.ktor.foodies.menu.persistence.ExposedMenuRepository
import io.ktor.foodies.menu.persistence.MenuItemsTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ExposedAdminRepository(database: Database) : ExposedMenuRepository(database), AdminRepository {

    override fun create(request: CreateMenuItem): MenuItem = transaction(database) {
        val returning = MenuItemsTable.insertReturning(
            listOf(
                MenuItemsTable.id,
                MenuItemsTable.createdAt,
                MenuItemsTable.updatedAt,
            )
        ) { row ->
            row[MenuItemsTable.name] = request.name
            row[MenuItemsTable.description] = request.description
            row[MenuItemsTable.imageUrl] = request.imageUrl
            row[MenuItemsTable.price] = request.price
            row[MenuItemsTable.stock] = request.stock
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
}

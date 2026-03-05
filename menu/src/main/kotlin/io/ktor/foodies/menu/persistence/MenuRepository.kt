package io.ktor.foodies.menu.persistence

import io.ktor.foodies.menu.MenuItem
import io.ktor.foodies.menu.admin.UpdateMenuItem
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

interface MenuRepository {
    fun findById(id: Long): MenuItem?
    fun list(offset: Int, limit: Int): List<MenuItem>
    fun update(id: Long, request: UpdateMenuItem): MenuItem?
}

class ExposedMenuRepository(private val database: Database) : MenuRepository {
    override fun list(offset: Int, limit: Int): List<MenuItem> = transaction(database) {
        MenuItemsTable.selectAll()
            .orderBy(MenuItemsTable.id to SortOrder.ASC)
            .offset(offset.toLong())
            .limit(limit)
            .map { it.toMenuItem() }
    }

    override fun findById(id: Long): MenuItem? = transaction(database) {
        MenuItemsTable.selectAll()
            .where { MenuItemsTable.id eq id }
            .map { it.toMenuItem() }
            .singleOrNull()
    }

    override fun update(id: Long, request: UpdateMenuItem): MenuItem? = transaction(database) {
        val existing = MenuItemsTable.selectAll()
            .where { MenuItemsTable.id eq id }
            .map { it.toMenuItem() }
            .singleOrNull()
            ?: return@transaction null

        val updated = existing.copy(
            name = request.name ?: existing.name,
            description = request.description ?: existing.description,
            imageUrl = request.imageUrl ?: existing.imageUrl,
            price = request.price ?: existing.price,
            stock = request.stock ?: existing.stock,
        )

        MenuItemsTable.update({ MenuItemsTable.id eq id }) { row ->
            row[name] = updated.name
            row[description] = updated.description
            row[imageUrl] = updated.imageUrl
            row[price] = updated.price
            row[stock] = updated.stock
        }

        updated
    }
}

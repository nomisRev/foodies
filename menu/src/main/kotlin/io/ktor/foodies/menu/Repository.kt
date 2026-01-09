package io.ktor.foodies.menu

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

interface MenuRepository {
    fun list(offset: Int, limit: Int): List<MenuItem>
    fun findById(id: Long): MenuItem?
    fun create(request: CreateMenuItem): MenuItem
    fun update(id: Long, request: UpdateMenuItem): MenuItem?
    fun delete(id: Long): Boolean
}

object MenuItemsTable : LongIdTable("menu_items") {
    val name = text("name")
    val description = text("description")
    val imageUrl = text("image_url")
    val price = decimal("price", 10, 2)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
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
        }.single()

        MenuItem(
            id = returning[MenuItemsTable.id].value,
            name = request.name,
            description = request.description,
            imageUrl = request.imageUrl,
            price = request.price,
            createdAt = returning[MenuItemsTable.createdAt],
            updatedAt = returning[MenuItemsTable.updatedAt],
        )
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
        )

        MenuItemsTable.update({ MenuItemsTable.id eq id }) { row ->
            row[name] = updated.name
            row[description] = updated.description
            row[imageUrl] = updated.imageUrl
            row[price] = updated.price
        }

        updated
    }

    override fun delete(id: Long): Boolean = transaction(database) {
        MenuItemsTable.deleteWhere { MenuItemsTable.id eq id } == 1
    }

    private fun ResultRow.toMenuItem(): MenuItem = MenuItem(
        id = this[MenuItemsTable.id].value,
        name = this[MenuItemsTable.name],
        description = this[MenuItemsTable.description],
        imageUrl = this[MenuItemsTable.imageUrl],
        price = this[MenuItemsTable.price],
        createdAt = this[MenuItemsTable.createdAt],
        updatedAt = this[MenuItemsTable.updatedAt],
    )
}

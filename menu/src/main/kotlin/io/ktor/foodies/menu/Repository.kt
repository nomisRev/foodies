package io.ktor.foodies.menu

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

interface MenuRepository {
    fun list(offset: Int, limit: Int, categoryId: Long? = null): List<MenuItem>
    fun findById(id: Long): MenuItem?
    fun create(request: CreateMenuItem): MenuItem
    fun update(id: Long, request: UpdateMenuItem): MenuItem?
    fun delete(id: Long): Boolean
    fun search(query: String): List<MenuItem>
    fun listCategories(): List<Category>
}

object CategoriesTable : LongIdTable("categories") {
    val name = text("name")
    val description = text("description")
    val iconUrl = text("icon_url").nullable()
    val displayOrder = integer("display_order").default(0)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

object MenuItemsTable : LongIdTable("menu_items") {
    val name = text("name")
    val description = text("description")
    val imageUrl = text("image_url")
    val price = decimal("price", 10, 2)
    val categoryId = reference("category_id", CategoriesTable)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}

class ExposedMenuRepository(private val database: Database) : MenuRepository {
    override fun list(offset: Int, limit: Int, categoryId: Long?): List<MenuItem> = transaction(database) {
        val query = (MenuItemsTable innerJoin CategoriesTable).selectAll()
        
        if (categoryId != null) {
            query.where { MenuItemsTable.categoryId eq categoryId }
        }

        query.orderBy(MenuItemsTable.id to SortOrder.ASC)
            .offset(offset.toLong())
            .limit(limit)
            .map { it.toMenuItem() }
    }

    override fun findById(id: Long): MenuItem? = transaction(database) {
        (MenuItemsTable innerJoin CategoriesTable).selectAll()
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
            row[categoryId] = request.categoryId
        }.single()

        val categoryName = CategoriesTable.selectAll()
            .where { CategoriesTable.id eq request.categoryId }
            .map { it[CategoriesTable.name] }
            .single()

        MenuItem(
            id = returning[MenuItemsTable.id].value,
            name = request.name,
            description = request.description,
            imageUrl = request.imageUrl,
            price = request.price,
            categoryId = request.categoryId,
            categoryName = categoryName,
            createdAt = returning[MenuItemsTable.createdAt],
            updatedAt = returning[MenuItemsTable.updatedAt],
        )
    }

    override fun update(id: Long, request: UpdateMenuItem): MenuItem? = transaction(database) {
        val existing = (MenuItemsTable innerJoin CategoriesTable).selectAll()
            .where { MenuItemsTable.id eq id }
            .map { it.toMenuItem() }
            .singleOrNull()
            ?: return@transaction null

        val updated = existing.copy(
            name = request.name ?: existing.name,
            description = request.description ?: existing.description,
            imageUrl = request.imageUrl ?: existing.imageUrl,
            price = request.price ?: existing.price,
            categoryId = request.categoryId ?: existing.categoryId,
        )

        MenuItemsTable.update({ MenuItemsTable.id eq id }) { row ->
            row[name] = updated.name
            row[description] = updated.description
            row[imageUrl] = updated.imageUrl
            row[price] = updated.price
            row[categoryId] = updated.categoryId
        }

        if (request.categoryId != null) {
            val newCategoryName = CategoriesTable.selectAll()
                .where { CategoriesTable.id eq request.categoryId }
                .map { it[CategoriesTable.name] }
                .single()
            updated.copy(categoryName = newCategoryName)
        } else {
            updated
        }
    }

    override fun delete(id: Long): Boolean = transaction(database) {
        MenuItemsTable.deleteWhere { MenuItemsTable.id eq id } == 1
    }

    override fun search(query: String): List<MenuItem> = transaction(database) {
        val q = "%${query.lowercase()}%"
        (MenuItemsTable innerJoin CategoriesTable).selectAll()
            .where { (MenuItemsTable.name.lowerCase() like q) or (MenuItemsTable.description.lowerCase() like q) }
            .map { it.toMenuItem() }
    }

    override fun listCategories(): List<Category> = transaction(database) {
        CategoriesTable.selectAll()
            .orderBy(CategoriesTable.displayOrder to SortOrder.ASC)
            .map {
                Category(
                    id = it[CategoriesTable.id].value,
                    name = it[CategoriesTable.name],
                    description = it[CategoriesTable.description],
                    iconUrl = it[CategoriesTable.iconUrl]
                )
            }
    }

    private fun ResultRow.toMenuItem(): MenuItem = MenuItem(
        id = this[MenuItemsTable.id].value,
        name = this[MenuItemsTable.name],
        description = this[MenuItemsTable.description],
        imageUrl = this[MenuItemsTable.imageUrl],
        price = this[MenuItemsTable.price],
        categoryId = this[MenuItemsTable.categoryId].value,
        categoryName = this[CategoriesTable.name],
        createdAt = this[MenuItemsTable.createdAt],
        updatedAt = this[MenuItemsTable.updatedAt],
    )
}

package io.ktor.foodies.menu.persistence

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

object MenuItemsTable : LongIdTable("menu_items") {
    val name = text("name")
    val description = text("description")
    val imageUrl = text("image_url")
    val price = decimal("price", 10, 2)
    val stock = integer("stock").default(0)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}

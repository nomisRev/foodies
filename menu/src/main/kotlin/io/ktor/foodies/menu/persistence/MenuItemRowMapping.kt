package io.ktor.foodies.menu.persistence

import io.ktor.foodies.menu.MenuItem
import org.jetbrains.exposed.v1.core.ResultRow

fun ResultRow.toMenuItem(): MenuItem = MenuItem(
    id = this[MenuItemsTable.id].value,
    name = this[MenuItemsTable.name],
    description = this[MenuItemsTable.description],
    imageUrl = this[MenuItemsTable.imageUrl],
    price = this[MenuItemsTable.price],
    stock = this[MenuItemsTable.stock],
    createdAt = this[MenuItemsTable.createdAt],
    updatedAt = this[MenuItemsTable.updatedAt],
)

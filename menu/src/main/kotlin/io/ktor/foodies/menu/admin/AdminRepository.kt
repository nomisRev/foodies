package io.ktor.foodies.menu.admin

import io.ktor.foodies.menu.MenuItem
import io.ktor.foodies.menu.persistence.MenuRepository

interface AdminRepository : MenuRepository {
    fun create(request: CreateMenuItem): MenuItem
    fun delete(id: Long): Boolean
}

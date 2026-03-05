package io.ktor.foodies.menu.admin

import io.ktor.foodies.menu.MenuItem

interface AdminRepository {
    fun create(request: CreateMenuItem): MenuItem
    fun delete(id: Long): Boolean
}

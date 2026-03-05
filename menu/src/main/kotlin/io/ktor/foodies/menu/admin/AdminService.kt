package io.ktor.foodies.menu.admin

import io.ktor.foodies.menu.MenuItem

interface AdminService {
    fun create(request: CreateMenuItemRequest): MenuItem
    fun update(id: Long, request: UpdateMenuItemRequest): MenuItem?
    fun delete(id: Long): Boolean
}

class AdminServiceImpl(private val repository: AdminRepository) : AdminService {
    override fun create(request: CreateMenuItemRequest): MenuItem =
        repository.create(request.validate())

    override fun update(id: Long, request: UpdateMenuItemRequest): MenuItem? =
        repository.update(id, request.validate())

    override fun delete(id: Long): Boolean = repository.delete(id)
}

package io.ktor.foodies.menu.admin

import io.ktor.foodies.menu.MenuItem
import io.ktor.foodies.menu.persistence.MenuRepository

interface AdminService {
    fun create(request: CreateMenuItemRequest): MenuItem
    fun update(id: Long, request: UpdateMenuItemRequest): MenuItem?
    fun delete(id: Long): Boolean
}

class AdminServiceImpl(
    private val adminRepository: AdminRepository,
    private val menuRepository: MenuRepository,
) : AdminService {
    override fun create(request: CreateMenuItemRequest): MenuItem =
        adminRepository.create(request.validate())

    override fun update(id: Long, request: UpdateMenuItemRequest): MenuItem? =
        menuRepository.update(id, request.validate())

    override fun delete(id: Long): Boolean = adminRepository.delete(id)
}

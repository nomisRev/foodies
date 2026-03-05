package io.ktor.foodies.menu.admin

import io.ktor.foodies.menu.persistence.MenuRepository
import io.ktor.server.routing.Routing

fun Routing.adminModule(adminRepository: AdminRepository, menuRepository: MenuRepository) {
    val service = AdminServiceImpl(adminRepository, menuRepository)
    adminRoutes(service)
}

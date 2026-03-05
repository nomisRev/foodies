package io.ktor.foodies.menu.admin

import io.ktor.server.routing.Routing

fun Routing.adminModule(repository: AdminRepository) {
    val service = AdminServiceImpl(repository)
    adminRoutes(service)
}

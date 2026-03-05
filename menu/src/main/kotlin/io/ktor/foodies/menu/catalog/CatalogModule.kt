package io.ktor.foodies.menu.catalog

import io.ktor.foodies.menu.persistence.MenuRepository
import io.ktor.server.routing.Routing

fun Routing.catalogModule(repository: MenuRepository) {
    val service = CatalogServiceImpl(repository)
    catalogRoutes(service)
}

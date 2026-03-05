package io.ktor.foodies.menu.catalog

import io.ktor.foodies.menu.toResponse
import io.ktor.foodies.server.getValue
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.catalogRoutes(catalogService: CatalogService) = route("/menu") {
    get {
        val offset: Int? by call.parameters
        val limit: Int? by call.parameters
        call.respond(catalogService.list(offset, limit).map { it.toResponse() })
    }

    get("/{id}") {
        val id: Long by call.parameters
        val menuItem = catalogService.get(id)
        if (menuItem == null) call.respond(HttpStatusCode.NotFound) else call.respond(menuItem.toResponse())
    }
}

package io.ktor.foodies.menu

import io.ktor.foodies.server.getValue
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.* // Import all routing extensions
import io.ktor.server.auth.* // Import authentication extensions

fun Route.menuRoutes(menuService: MenuService) = authenticate("auth-jwt") {
    route("/menu") {
        get {
            val offset: Int? by call.parameters
            val limit: Int? by call.parameters
            val menuItems = menuService.list(offset, limit).map { it.toResponse() }
            call.respond(menuItems)
        }

        get("/{id}") {
            val id: Long by call.parameters
            val menuItem = menuService.get(id)
            if (menuItem == null) call.respond(HttpStatusCode.NotFound) else call.respond(menuItem.toResponse())
        }

        post {
            val request = call.receive<CreateMenuItemRequest>()
            val created = menuService.create(request)
            call.respond(HttpStatusCode.Created, created.toResponse())
        }

        put("/{id}") {
            val id: Long by call.parameters
            val request = call.receive<UpdateMenuItemRequest>()
            val updated = menuService.update(id, request)
            if (updated == null) call.respond(HttpStatusCode.NotFound) else call.respond(updated.toResponse())
        }

        delete("/{id}") {
            val id: Long by call.parameters
            val deleted = menuService.delete(id)
            if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound)
        }
    }
}
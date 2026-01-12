package io.ktor.foodies.menu

import io.ktor.foodies.server.getValue
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.menuRoutes(menuService: MenuService) = route("/menu") {
    get {
        val offset: Int? by call.parameters
        val limit: Int? by call.parameters
        val categoryId: Long? by call.parameters
        val menuItems = menuService.list(offset, limit, categoryId).map { it.toResponse() }
        call.respond(menuItems)
    }

    get("/categories") {
        val categories = menuService.listCategories().map { it.toResponse() }
        call.respond(categories)
    }

    get("/{id}") {
        val id: Long by call.parameters
        val menuItem = menuService.get(id)
        if (menuItem == null) call.respond(HttpStatusCode.NotFound) else call.respond(menuItem.toResponse())
    }

    get("/search") {
        val query = call.request.queryParameters["q"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val menuItems = menuService.search(query).map { it.toResponse() }
        call.respond(menuItems)
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
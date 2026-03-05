package io.ktor.foodies.menu.admin

import io.ktor.foodies.menu.toResponse
import io.ktor.foodies.server.getValue
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.adminRoutes(adminService: AdminService) = route("/menu") {
    post {
        val request = call.receive<CreateMenuItemRequest>()
        val created = adminService.create(request)
        call.respond(HttpStatusCode.Created, created.toResponse())
    }

    put("/{id}") {
        val id: Long by call.parameters
        val request = call.receive<UpdateMenuItemRequest>()
        val updated = adminService.update(id, request)
        if (updated == null) call.respond(HttpStatusCode.NotFound) else call.respond(updated.toResponse())
    }

    delete("/{id}") {
        val id: Long by call.parameters
        val deleted = adminService.delete(id)
        if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound)
    }
}

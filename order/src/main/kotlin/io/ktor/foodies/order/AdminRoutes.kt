package io.ktor.foodies.order

import io.ktor.foodies.order.domain.OrderStatus
import io.ktor.foodies.order.service.OrderService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.adminRoutes(orderService: OrderService) = authenticate {
    route("/admin/orders") {
        get {
            call.requireAdmin {
                val offset = call.parameters["offset"]?.toLongOrNull() ?: 0L
                val limit = call.parameters["limit"]?.toIntOrNull() ?: 20
                val status =
                    call.parameters["status"]?.let { runCatching { OrderStatus.valueOf(it) }.getOrNull() }
                val buyerId = call.parameters["buyerId"]

                val orders = orderService.getAllOrders(offset, limit.coerceAtMost(100), status, buyerId)
                call.respond(orders)
            }
        }

        put("/{id}/ship") {
            call.requireAdmin {
                val requestIdString = call.request.header("X-Request-Id")
                    ?: throw IllegalArgumentException("X-Request-Id header is required")
                val requestId = java.util.UUID.fromString(requestIdString)
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: throw IllegalArgumentException("Order ID is required")

                val order = orderService.shipOrder(requestId, id)
                    ?: return@requireAdmin call.respond(HttpStatusCode.NotFound, "Order not found")
                call.respond(order)
            }
        }
    }
}

// TODO: Introduce Authentication + Role-based Authorization Plugin
private suspend fun ApplicationCall.requireAdmin(block: suspend () -> Unit) {
    val principal = principal<JWTPrincipal>()!!
    val realmAccess = principal.payload.getClaim("realm_access").asMap()
    val roles = realmAccess["roles"] as? List<String> ?: emptyList()

    if (!roles.contains("admin")) {
        respond(HttpStatusCode.Forbidden, "Admin role required")
    } else {
        block()
    }
}

package io.ktor.foodies.order.admin

import io.ktor.foodies.events.order.OrderStatus
import io.ktor.foodies.order.fulfillment.FulfillmentService
import io.ktor.foodies.order.tracking.TrackingService
import io.ktor.foodies.server.auth.secureUser
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.adminRoutes(trackingService: TrackingService, fulfillmentService: FulfillmentService) = secureUser("admin") {
    route("/admin/orders") {
        get {
            val offset = call.parameters["offset"]?.toLongOrNull() ?: 0L
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 20
            val status =
                call.parameters["status"]?.let { runCatching { OrderStatus.valueOf(it) }.getOrNull() }
            val buyerId = call.parameters["buyerId"]

            val orders = trackingService.getAllOrders(offset, limit.coerceAtMost(100), status, buyerId)
            call.respond(orders)
        }

        put("/{id}/ship") {
            val requestIdString = call.request.header("X-Request-Id")
                ?: throw IllegalArgumentException("X-Request-Id header is required")
            val requestId = java.util.UUID.fromString(requestIdString)
            val id = call.parameters["id"]?.toLongOrNull()
                ?: throw IllegalArgumentException("Order ID is required")

            val order = fulfillmentService.shipOrder(requestId, id)
                ?: return@put call.respond(HttpStatusCode.NotFound, "Order not found")
            call.respond(order)
        }
    }
}

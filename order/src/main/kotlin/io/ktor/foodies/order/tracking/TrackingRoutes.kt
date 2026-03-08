package io.ktor.foodies.order.tracking

import io.ktor.foodies.events.common.CardBrand
import io.ktor.foodies.events.order.OrderStatus
import io.ktor.foodies.server.auth.secureUser
import io.ktor.foodies.server.auth.userPrincipal
import io.ktor.foodies.server.getValue
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.trackingRoutes(trackingService: TrackingService) = secureUser {
    route("/orders") {
        get {
            val buyerId = userPrincipal().userId
            val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
            val status = call.request.queryParameters["status"]?.let { OrderStatus.valueOf(it) }

            val orders = trackingService.getOrders(buyerId, offset, limit, status)
            call.respond(orders)
        }

        get("/card-types") {
            val cardTypes = CardBrand.entries.map { CardBrandResponse(it.name, it.displayName) }
            call.respond(cardTypes)
        }

        get("/{id}") {
            val buyerId = userPrincipal().userId
            val id: Long by call.parameters

            when (val result = trackingService.getOrder(id, buyerId)) {
                is GetOrderResult.Success -> call.respond(result.order)
                is GetOrderResult.NotFound -> call.respond(HttpStatusCode.NotFound, "Order not found")
                is GetOrderResult.Forbidden -> call.respond(HttpStatusCode.Forbidden, "Access denied to order")
            }
        }

        put("/{id}/cancel") {
            val requestIdString = call.request.header("X-Request-Id")
                ?: throw IllegalArgumentException("X-Request-Id header is required")
            val requestId = java.util.UUID.fromString(requestIdString)
            val buyerId = userPrincipal().userId
            val id: Long by call.parameters

            val reason = call.receive<CancelOrderRequest>().reason

            val order = trackingService.cancelOrder(requestId, id, buyerId, reason)
            call.respond(order)
        }
    }
}

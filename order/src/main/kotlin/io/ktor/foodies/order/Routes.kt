package io.ktor.foodies.order

import io.ktor.foodies.events.common.CardBrand
import io.ktor.foodies.events.order.OrderStatus
import io.ktor.foodies.order.domain.CancelOrderRequest
import io.ktor.foodies.order.domain.CardBrandResponse
import io.ktor.foodies.order.domain.CreateOrderRequest
import io.ktor.foodies.order.service.OrderService
import io.ktor.foodies.server.getValue
import io.ktor.foodies.server.openid.authenticatedUser
import io.ktor.foodies.server.openid.userPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.orderRoutes(orderService: OrderService) = authenticatedUser {
    route("/orders") {
        get {
            val user = userPrincipal()
            val buyerId = user.userId
            val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
            val status = call.request.queryParameters["status"]?.let { OrderStatus.valueOf(it) }

            val orders = orderService.getOrders(buyerId, offset, limit, status)
            call.respond(orders)
        }

        get("/card-types") {
            val cardTypes = CardBrand.entries.map { CardBrandResponse(it.name, it.displayName) }
            call.respond(cardTypes)
        }

        get("/{id}") {
            val user = userPrincipal()
            val buyerId = user.userId
            val id: Long by call.parameters

            when (val result = orderService.getOrder(id, buyerId)) {
                is io.ktor.foodies.order.domain.GetOrderResult.Success -> call.respond(result.order)
                is io.ktor.foodies.order.domain.GetOrderResult.NotFound -> call.respond(HttpStatusCode.NotFound, "Order not found")
                is io.ktor.foodies.order.domain.GetOrderResult.Forbidden -> call.respond(HttpStatusCode.Forbidden, "Access denied to order")
            }
        }

        post {
            val requestIdString = call.request.header("X-Request-Id")
                ?: throw IllegalArgumentException("X-Request-Id header is required")
            val requestId = java.util.UUID.fromString(requestIdString)
            val user = userPrincipal()
            val buyerId = user.userId
            val buyerEmail = user.email ?: throw IllegalStateException("User email is missing")
            val buyerName = user.name ?: "Unknown"

            val request = call.receive<CreateOrderRequest>()
            val order = orderService.createOrder(requestId, buyerId, buyerEmail, buyerName, request)
            call.respond(HttpStatusCode.Created, order)
        }

        put("/{id}/cancel") {
            val requestIdString = call.request.header("X-Request-Id")
                ?: throw IllegalArgumentException("X-Request-Id header is required")
            val requestId = java.util.UUID.fromString(requestIdString)
            val user = userPrincipal()
            val buyerId = user.userId
            val id: Long by call.parameters
            
            val body = runCatching { call.receive<CancelOrderRequest>() }.getOrNull()
            val reason = body?.reason ?: "Cancelled by user"

            val order = orderService.cancelOrder(requestId, id, buyerId, reason)
            call.respond(order)
        }
    }
}

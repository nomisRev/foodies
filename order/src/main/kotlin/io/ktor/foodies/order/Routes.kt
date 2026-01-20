package io.ktor.foodies.order

import io.ktor.foodies.events.common.CardBrand
import io.ktor.foodies.events.order.OrderStatus
import io.ktor.foodies.order.database.Orders.buyerEmail
import io.ktor.foodies.order.database.Orders.buyerName
import io.ktor.foodies.order.domain.CancelOrderRequest
import io.ktor.foodies.order.domain.CardBrandResponse
import io.ktor.foodies.order.domain.CreateOrderRequest
import io.ktor.foodies.order.domain.GetOrderResult
import io.ktor.foodies.order.service.OrderService
import io.ktor.foodies.server.getValue
import io.ktor.foodies.server.openid.ServicePrincipal
import io.ktor.foodies.server.openid.UserPrincipal
import io.ktor.foodies.server.openid.authPrincipal
import io.ktor.foodies.server.openid.authenticated
import io.ktor.foodies.server.openid.authenticatedUser
import io.ktor.foodies.server.openid.requireAdminOrScope
import io.ktor.foodies.server.openid.requireRole
import io.ktor.foodies.server.openid.userPrincipal
import io.ktor.foodies.server.openid.withAuthContext
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun Route.orderRoutes(orderService: OrderService) = authenticated {
    route("/orders") {
        requireAdminOrScope("order:read") {
            get {
                val user = authPrincipal()
                val buyerId = when (user) {
                    is UserPrincipal -> user.userId
                    is ServicePrincipal -> call.request.queryParameters["buyerId"]
                        ?: throw IllegalArgumentException("buyerId is required for service access")
                }
                val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
                val status = call.request.queryParameters["status"]?.let { OrderStatus.valueOf(it) }

                val orders = call.withAuthContext { orderService.getOrders(buyerId, offset, limit, status) }
                call.respond(orders)
            }

            requireRole("admin") {
                get("/all") {
                    val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
                    val status = call.request.queryParameters["status"]?.let { OrderStatus.valueOf(it) }
                    val buyerId = call.request.queryParameters["buyerId"]

                    val orders = call.withAuthContext { orderService.getAllOrders(offset, limit, status, buyerId) }
                    call.respond(orders)
                }
            }

            get("/card-types") {
                val cardTypes = CardBrand.entries.map { CardBrandResponse(it.name, it.displayName) }
                call.respond(cardTypes)
            }

            get("/{id}") {
                val principal = authPrincipal()
                val id: Long by call.parameters

                val orderResult = call.withAuthContext {
                    when (principal) {
                        is UserPrincipal -> {
                            if (principal.roles.contains("admin")) orderService.getOrder(id)
                            else orderService.getOrder(id, principal.userId)
                        }
                        is ServicePrincipal -> {
                            val buyerId = call.request.queryParameters["buyerId"]
                            orderService.getOrder(id, buyerId)
                        }
                    }
                }

                when (orderResult) {
                    is GetOrderResult.Success -> call.respond(orderResult.order)
                    is GetOrderResult.NotFound -> call.respond(HttpStatusCode.NotFound, "Order not found")
                    is GetOrderResult.Forbidden -> call.respond(HttpStatusCode.Forbidden, "Access denied to order")
                }
            }
        }

        requireAdminOrScope("order:write") {
            post {
                val requestIdString =
                    requireNotNull(call.request.header("X-Request-Id")) { "X-Request-Id header is required" }
                val requestId = Uuid.parse(requestIdString)
                val user = userPrincipal()

                val request = call.receive<CreateOrderRequest>()
                val order = call.withAuthContext {
                    orderService.createOrder(
                        requestId,
                        user.userId,
                        user.email ?: "",
                        user.name ?: user.userId,
                        request
                    )
                }
                call.respond(HttpStatusCode.Created, order)
            }

            put("/{id}/cancel") {
                val requestIdString = call.request.header("X-Request-Id")
                    ?: throw IllegalArgumentException("X-Request-Id header is required")
                val requestId = Uuid.parse(requestIdString)
                val user = userPrincipal()
                val buyerId = user.userId
                val id: Long by call.parameters

                val body = runCatching { call.receive<CancelOrderRequest>() }.getOrNull()
                val reason = body?.reason ?: "Cancelled by user"

                val order = call.withAuthContext { orderService.cancelOrder(requestId, id, buyerId, reason) }
                call.respond(order)
            }
        }
    }
}

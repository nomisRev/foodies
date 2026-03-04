package io.ktor.foodies.order.placement

import io.ktor.foodies.server.auth.secureUser
import io.ktor.foodies.server.auth.userPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

private fun JWTPrincipal.buyerName(): String = payload.getClaim("name").asString()
    ?: payload.getClaim("preferred_username").asString()
    ?: "Unknown"

fun Route.placementRoutes(placementService: PlacementService) = secureUser {
    route("/orders") {
        post {
            val requestIdString = call.request.header("X-Request-Id")
                ?: throw IllegalArgumentException("X-Request-Id header is required")
            val requestId = java.util.UUID.fromString(requestIdString)
            val buyerId = userPrincipal().userId
            val buyerEmail = userPrincipal().email ?: throw IllegalStateException("User email is required")
            val buyerName = call.principal<JWTPrincipal>()?.buyerName() ?: "Unknown"
            val token = userPrincipal().accessToken

            val request = call.receive<CreateOrderRequest>()
            val order = placementService.createOrder(requestId, buyerId, buyerEmail, buyerName, request, token)
            call.respond(HttpStatusCode.Created, order)
        }
    }
}

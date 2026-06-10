package io.ktor.foodies.order.placement

import io.ktor.foodies.server.auth.*
import io.ktor.http.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import java.util.*

@OptIn(ExperimentalKtorApi::class)
fun Route.placementRoutes(placementService: PlacementService) = secure {
    route("/orders") {
        post {
            val requestIdString = call.requireHeader("X-Request-Id")
            val requestId = UUID.fromString(requestIdString)
            val buyerId = principal.userId
            val buyerEmail = checkNotNull(principal.email) { "User email is required" }
            val buyerName = principal.name ?: "Unknown"
            val token = principal.accessToken.value

            val request = call.receive<CreateOrderRequest>()
            val order = placementService.createOrder(requestId, buyerId, buyerEmail, buyerName, request, token)
            call.respond(HttpStatusCode.Created, order)
        }
    }
}

package io.ktor.foodies.basket

import io.ktor.foodies.server.getValue
import io.ktor.foodies.server.openid.AUTH_SERVICE
import io.ktor.foodies.server.openid.AUTH_USER
import io.ktor.foodies.server.openid.ServicePrincipal
import io.ktor.foodies.server.validate
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("BasketRoutes")

private fun JWTPrincipal.buyerId(): String = payload.subject
    ?: throw IllegalStateException("JWT subject claim is missing")

fun Route.basketRoutes(basketService: BasketService) {
    basketUserRoutes(basketService)
    basketServiceRoutes(basketService)
}

/**
 * User-to-service API: Routes for end-user interactions with their basket.
 * Users can only access and modify their own basket (buyerId derived from JWT subject).
 */
fun Route.basketUserRoutes(basketService: BasketService) = authenticate(AUTH_USER) {
    route("/basket") {
        get {
            val buyerId = call.principal<JWTPrincipal>()!!.buyerId()
            val basket = basketService.getBasket(buyerId)
            call.respond(basket)
        }

        delete {
            val buyerId = call.principal<JWTPrincipal>()!!.buyerId()
            basketService.clearBasket(buyerId)
            call.respond(HttpStatusCode.NoContent)
        }

        route("/items") {
            post {
                val buyerId = call.principal<JWTPrincipal>()!!.buyerId()
                val request = call.receive<AddItemRequest>()
                val validatedRequest = validate { request.validate() }
                val basket = basketService.addItem(buyerId, validatedRequest)
                if (basket == null) call.respond(HttpStatusCode.NotFound) else call.respond(basket)
            }

            put("/{itemId}") {
                val buyerId = call.principal<JWTPrincipal>()!!.buyerId()
                val itemId: String by call.parameters
                val request = call.receive<UpdateItemQuantityRequest>()
                val validatedRequest = request.validate()
                val basket = basketService.updateItemQuantity(buyerId, itemId, validatedRequest)
                if (basket == null) call.respond(HttpStatusCode.NotFound) else call.respond(basket)
            }

            delete("/{itemId}") {
                val buyerId = call.principal<JWTPrincipal>()!!.buyerId()
                val itemId: String by call.parameters
                val basket = basketService.removeItem(buyerId, itemId)
                if (basket == null) call.respond(HttpStatusCode.NotFound) else call.respond(basket)
            }
        }
    }
}

/**
 * Service-to-service API: Routes for internal service communication.
 * Read-only access - services cannot modify user baskets directly.
 */
fun Route.basketServiceRoutes(basketService: BasketService) = authenticate(AUTH_SERVICE) {
    get("/internal/basket/{userId}") {
        val servicePrincipal = call.principal<ServicePrincipal>()!!
        val userId: String by call.parameters

        logger.info("Service {} accessing basket for user {}", servicePrincipal.serviceId, userId)
        val basket = basketService.getBasket(userId)
        call.respond(basket)
    }
}

package io.ktor.foodies.basket

import io.ktor.foodies.server.getValue
import io.ktor.foodies.server.openid.serviceCredentials
import io.ktor.foodies.server.openid.userCredentials
import io.ktor.foodies.server.openid.withServiceScope
import io.ktor.foodies.server.openid.withUserScope
import io.ktor.foodies.server.validate
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
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

fun Route.basketRoutes(basketService: BasketService) {
    basketUserRoutes(basketService)
    basketServiceRoutes(basketService)
}

/**
 * User-to-service API: Routes for end-user interactions with their basket.
 * Users can only access and modify their own basket (buyerId derived from JWT subject).
 */
fun Route.basketUserRoutes(basketService: BasketService) = withUserScope {
    route("/basket") {
        get {
            val user = userCredentials()
            val basket = basketService.getBasket(user.subject)
            call.respond(basket)
        }

        delete {
            val user = userCredentials()
            basketService.clearBasket(user.subject)
            call.respond(HttpStatusCode.NoContent)
        }

        route("/items") {
            post {
                val user = userCredentials()
                val request = call.receive<AddItemRequest>()
                val validatedRequest = validate { request.validate() }
                val basket = basketService.addItem(user.subject, validatedRequest)
                if (basket == null) call.respond(HttpStatusCode.NotFound) else call.respond(basket)
            }

            put("/{itemId}") {
                val user = userCredentials()
                val itemId: String by call.parameters
                val request = call.receive<UpdateItemQuantityRequest>()
                val validatedRequest = request.validate()
                val basket = basketService.updateItemQuantity(user.subject, itemId, validatedRequest)
                if (basket == null) call.respond(HttpStatusCode.NotFound) else call.respond(basket)
            }

            delete("/{itemId}") {
                val user = userCredentials()
                val itemId: String by call.parameters
                val basket = basketService.removeItem(user.subject, itemId)
                if (basket == null) call.respond(HttpStatusCode.NotFound) else call.respond(basket)
            }
        }
    }
}

/**
 * Service-to-service API: Routes for internal service communication.
 * Read-only access - services cannot modify user baskets directly.
 */
fun Route.basketServiceRoutes(basketService: BasketService) = withServiceScope {
    get("/internal/basket/{userId}") {
        val servicePrincipal = serviceCredentials()
        val userId: String by call.parameters

        logger.info("Service {} accessing basket for user {}", servicePrincipal.serviceId, userId)
        val basket = basketService.getBasket(userId)
        call.respond(basket)
    }
}

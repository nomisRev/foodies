package io.ktor.foodies.basket

import io.ktor.foodies.server.getValue
import io.ktor.foodies.server.openid.*
import io.ktor.foodies.server.validate
import io.ktor.http.HttpStatusCode
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
fun Route.basketUserRoutes(basketService: BasketService) = authenticatedUser {
    route("/basket") {
        requireAdminOrScope("basket:read") {
            get {
                val user = userPrincipal()
                val basket = call.withAuthContext { basketService.getBasket(user.userId) }
                call.respond(basket)
            }
        }

        requireAdminOrScope("basket:write") {
            delete {
                val user = userPrincipal()
                call.withAuthContext { basketService.clearBasket(user.userId) }
                call.respond(HttpStatusCode.NoContent)
            }

            route("/items") {
                post {
                    val user = userPrincipal()
                    val request = call.receive<AddItemRequest>()
                    val validatedRequest = validate { request.validate() }
                    val basket = call.withAuthContext { basketService.addItem(user.userId, validatedRequest) }
                    if (basket == null) call.respond(HttpStatusCode.NotFound) else call.respond(basket)
                }

                put("/{itemId}") {
                    val user = userPrincipal()
                    val itemId: String by call.parameters
                    val request = call.receive<UpdateItemQuantityRequest>()
                    val validatedRequest = request.validate()
                    val basket =
                        call.withAuthContext { basketService.updateItemQuantity(user.userId, itemId, validatedRequest) }
                    if (basket == null) call.respond(HttpStatusCode.NotFound) else call.respond(basket)
                }

                delete("/{itemId}") {
                    val user = userPrincipal()
                    val itemId: String by call.parameters
                    val basket = call.withAuthContext { basketService.removeItem(user.userId, itemId) }
                    if (basket == null) call.respond(HttpStatusCode.NotFound) else call.respond(basket)
                }
            }
        }
    }
}

/**
 * Service-to-service API: Routes for internal service communication.
 * Read-only access - services cannot modify user baskets directly.
 * Supports both user tokens (delegation) and service tokens.
 */
fun Route.basketServiceRoutes(basketService: BasketService) = authenticated {
    requireScope("basket:read") {
        get("/internal/basket/{userId}") {
            val userId: String by call.parameters
            val principal = authPrincipal()

            if (principal is UserPrincipal && principal.userId != userId && !principal.roles.contains("admin")) {
                call.respond(HttpStatusCode.Forbidden, "Cannot access another user's basket")
                return@get
            }

            val basket = basketService.getBasket(userId)
            call.respond(basket)
        }
    }
}

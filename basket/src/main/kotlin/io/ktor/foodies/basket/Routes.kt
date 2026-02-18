package io.ktor.foodies.basket

import io.ktor.foodies.server.auth.secureUser
import io.ktor.foodies.server.auth.userPrincipal
import io.ktor.foodies.server.getValue
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

fun Route.basketRoutes(basketService: BasketService) = secureUser {
    route("/basket") {
        get {
            val buyerId = userPrincipal().userId
            val basket = basketService.getBasket(buyerId)
            call.respond(basket)
        }

        delete {
            val buyerId = userPrincipal().userId
            basketService.clearBasket(buyerId)
            call.respond(HttpStatusCode.NoContent)
        }

        route("/items") {
            post {
                val buyerId = userPrincipal().userId
                val request = call.receive<AddItemRequest>()
                val validatedRequest = validate { request.validate() }
                val basket = basketService.addItem(buyerId, validatedRequest)
                if (basket == null) call.respond(HttpStatusCode.NotFound) else call.respond(basket)
            }

            put("/{itemId}") {
                val buyerId = userPrincipal().userId
                val itemId: String by call.parameters
                val request = call.receive<UpdateItemQuantityRequest>()
                val validatedRequest = request.validate()
                val basket = basketService.updateItemQuantity(buyerId, itemId, validatedRequest)
                if (basket == null) call.respond(HttpStatusCode.NotFound) else call.respond(basket)
            }

            delete("/{itemId}") {
                val buyerId = userPrincipal().userId
                val itemId: String by call.parameters
                val basket = basketService.removeItem(buyerId, itemId)
                if (basket == null) call.respond(HttpStatusCode.NotFound) else call.respond(basket)
            }
        }
    }
}

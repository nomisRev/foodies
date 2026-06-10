package io.ktor.foodies.basket

import io.ktor.foodies.server.*
import io.ktor.foodies.server.auth.*
import io.ktor.http.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*

@OptIn(ExperimentalKtorApi::class)
fun Route.basketRoutes(basketService: BasketService) = secure {
    route("/basket") {
        get {
            val buyerId = principal.userId
            val basket = basketService.getBasket(buyerId)
            call.respond(basket)
        }

        delete {
            val buyerId = principal.userId
            basketService.clearBasket(buyerId)
            call.respond(HttpStatusCode.NoContent)
        }

        route("/items") {
            post {
                val buyerId = principal.userId
                val request = call.receive<AddItemRequest>()
                val validatedRequest = validate { request.validate() }
                val basket = basketService.addItem(buyerId, validatedRequest)
                if (basket == null) call.respond(HttpStatusCode.NotFound) else call.respond(basket)
            }

            put("/{itemId}") {
                val buyerId = principal.userId
                val itemId: String by call.parameters
                val request = call.receive<UpdateItemQuantityRequest>()
                val validatedRequest = request.validate()
                val basket = basketService.updateItemQuantity(buyerId, itemId, validatedRequest)
                if (basket == null) call.respond(HttpStatusCode.NotFound) else call.respond(basket)
            }

            delete("/{itemId}") {
                val buyerId = principal.userId
                val itemId: String by call.parameters
                val basket = basketService.removeItem(buyerId, itemId)
                if (basket == null) call.respond(HttpStatusCode.NotFound) else call.respond(basket)
            }
        }
    }
}

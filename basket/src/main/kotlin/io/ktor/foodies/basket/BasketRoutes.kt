package io.ktor.foodies.basket

import io.ktor.foodies.basket.routes.BasketApi
import io.ktor.foodies.basket.routes.BasketError
import io.ktor.foodies.server.ValidationException
import io.ktor.foodies.server.auth.UserPrincipal
import io.ktor.foodies.server.auth.secureUser
import io.ktor.foodies.server.validate
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import opensavvy.spine.server.fail
import opensavvy.spine.server.respond
import opensavvy.spine.server.route

fun Route.basketRoutes(basketService: BasketService) = secureUser {
    route(BasketApi.get) {
        val buyerId = requireNotNull(call.principal<UserPrincipal>()).userId
        respond(basketService.getBasket(buyerId))
    }

    route(BasketApi.clear) {
        val buyerId = requireNotNull(call.principal<UserPrincipal>()).userId
        basketService.clearBasket(buyerId)
        respond()
    }

    route(BasketApi.Items.add) {
        val buyerId = requireNotNull(call.principal<UserPrincipal>()).userId
        val validatedRequest = try {
            validate { body.validate() }
        } catch (e: ValidationException) {
            call.respond(HttpStatusCode.BadRequest, BasketError.InvalidRequest(e.reasons))
            return@route
        }
        val basket = basketService.addItem(buyerId, validatedRequest)
        if (basket == null) fail(BasketError.NotFound("Menu item not found"))
        else respond(basket)
    }

    route(BasketApi.Items.Item.update) {
        val buyerId = requireNotNull(call.principal<UserPrincipal>()).userId
        val itemId = idOf(BasketApi.Items.Item)
        val validatedRequest = try {
            validate { body.validate() }
        } catch (e: ValidationException) {
            call.respond(HttpStatusCode.BadRequest, BasketError.InvalidRequest(e.reasons))
            return@route
        }
        val basket = basketService.updateItemQuantity(buyerId, itemId, validatedRequest)
        if (basket == null) fail(BasketError.NotFound("Item not found in basket"))
        else respond(basket)
    }

    route(BasketApi.Items.Item.remove) {
        val buyerId = requireNotNull(call.principal<UserPrincipal>()).userId
        val itemId = idOf(BasketApi.Items.Item)
        val basket = basketService.removeItem(buyerId, itemId)
        if (basket == null) fail(BasketError.NotFound("Item not found in basket"))
        else respond(basket)
    }
}

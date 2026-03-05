package io.ktor.foodies.server.cart

import io.ktor.client.HttpClient
import io.ktor.foodies.server.Config
import io.ktor.server.application.Application

data class CartModule(val cartService: CartService)

fun Application.cartModule(config: Config.Basket, httpClient: HttpClient): CartModule {
    val cartService = HttpCartService(config.baseUrl, httpClient)
    cartRoutes(cartService)
    return CartModule(cartService = cartService)
}

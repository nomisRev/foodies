package io.ktor.foodies.server.cart

import io.ktor.foodies.server.UserSession
import io.ktor.foodies.server.basket.BasketItem
import io.ktor.foodies.server.basket.BasketService
import io.ktor.foodies.server.basket.CustomerBasket
import io.ktor.foodies.server.respondHtmxFragment
import io.ktor.foodies.server.respondHtmlWithLayout
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.htmx.hx
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.utils.io.ExperimentalKtorApi
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.TagConsumer
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.hiddenInput
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.numberInput
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span

@OptIn(ExperimentalKtorApi::class)
fun Application.cartRoutes(basketService: BasketService) {
    routing {
        // Cart badge - returns just the badge HTML fragment
        hx {
            get("/cart/badge") {
                val session = call.sessions.get<UserSession>()
                val itemCount = if (session != null) {
                    runCatching { basketService.getBasket(session.idToken).items.sumOf { it.quantity } }
                        .getOrDefault(0)
                } else {
                    0
                }
                call.respondHtmxFragment { cartBadge(itemCount) }
            }
        }

        // Full cart page - requires authentication
        authenticate {
            get("/cart") {
                val session = call.sessions.get<UserSession>()
                    ?: return@get call.respondRedirect("/login")

                val basket = runCatching { basketService.getBasket(session.idToken) }
                    .getOrElse { CustomerBasket(buyerId = "", items = emptyList()) }

                call.respondHtmlWithLayout("Foodies - Your Cart", true, listOf("/static/cart.css")) {
                    cartPage(basket)
                }
            }
        }

        // HTMX-powered cart operations
        hx {
            // Add item to cart
            post("/cart/items") {
                val session = call.sessions.get<UserSession>()
                if (session == null) {
                    // Return redirect header for HTMX to handle
                    call.response.headers.append("HX-Redirect", "/login")
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                val params = call.receiveParameters()
                val menuItemId = params["menuItemId"]?.toLongOrNull()
                val quantity = params["quantity"]?.toIntOrNull() ?: 1

                if (menuItemId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing menuItemId")
                    return@post
                }

                val result = runCatching { basketService.addItem(session.idToken, menuItemId, quantity) }

                if (result.isSuccess) {
                    val basket = result.getOrThrow()
                    val itemCount = basket.items.sumOf { it.quantity }
                    // Return updated badge with OOB swap
                    call.respondHtmxFragment {
                        cartBadge(itemCount, isOob = true)
                        addToCartSuccess()
                    }
                } else {
                    call.respond(HttpStatusCode.InternalServerError, "Failed to add item to cart")
                }
            }

            // Update item quantity
            put("/cart/items/{itemId}") {
                val session = call.sessions.get<UserSession>()
                    ?: return@put call.respond(HttpStatusCode.Unauthorized)

                val itemId = call.parameters["itemId"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest)

                val params = call.receiveParameters()
                val quantity = params["quantity"]?.toIntOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid quantity")

                val result = runCatching { basketService.updateItemQuantity(session.idToken, itemId, quantity) }

                if (result.isSuccess) {
                    val basket = result.getOrThrow()
                    call.respondHtmxFragment {
                        cartItemsFragment(basket)
                        cartSummary(basket, isOob = true)
                        cartBadge(basket.items.sumOf { it.quantity }, isOob = true)
                    }
                } else {
                    call.respond(HttpStatusCode.InternalServerError, "Failed to update item")
                }
            }

            // Remove item from cart
            delete("/cart/items/{itemId}") {
                val session = call.sessions.get<UserSession>()
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized)

                val itemId = call.parameters["itemId"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)

                val result = runCatching { basketService.removeItem(session.idToken, itemId) }

                if (result.isSuccess) {
                    val basket = result.getOrThrow()
                    call.respondHtmxFragment {
                        cartItemsFragment(basket)
                        cartSummary(basket, isOob = true)
                        cartBadge(basket.items.sumOf { it.quantity }, isOob = true)
                    }
                } else {
                    call.respond(HttpStatusCode.InternalServerError, "Failed to remove item")
                }
            }

            // Clear entire cart
            delete("/cart") {
                val session = call.sessions.get<UserSession>()
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized)

                runCatching { basketService.clearBasket(session.idToken) }

                val emptyBasket = CustomerBasket(buyerId = "", items = emptyList())
                call.respondHtmxFragment {
                    cartItemsFragment(emptyBasket)
                    cartSummary(emptyBasket, isOob = true)
                    cartBadge(0, isOob = true)
                }
            }
        }
    }
}

// Cart badge component
private fun TagConsumer<*>.cartBadge(itemCount: Int, isOob: Boolean = false) {
    a(href = "/cart", classes = "cart-link") {
        id = "cart-badge"
        if (isOob) attributes["hx-swap-oob"] = "true"
        attributes["hx-get"] = "/cart/badge"
        attributes["hx-trigger"] = "cart-updated from:body"
        attributes["hx-swap"] = "outerHTML"
        span(classes = "cart-icon") {
            +"Cart"
        }
        if (itemCount > 0) {
            span(classes = "cart-count") { +itemCount.toString() }
        }
    }
}

// Success feedback after adding to cart
private fun TagConsumer<*>.addToCartSuccess() {
    div(classes = "toast success") {
        id = "toast"
        attributes["hx-swap-oob"] = "true"
        +"Added to cart!"
    }
}

// Full cart page content
private fun FlowContent.cartPage(basket: CustomerBasket) {
    section(classes = "cart-section") {
        h1 { +"Your Cart" }

        if (basket.items.isEmpty()) {
            consumer.cartEmpty()
        } else {
            div(classes = "cart-container") {
                div(classes = "cart-items") {
                    id = "cart-items"
                    basket.items.forEach { item ->
                        consumer.cartItemCard(item)
                    }
                }

                consumer.cartSummary(basket)
            }
        }
    }
}

// Cart items fragment for HTMX swap
private fun TagConsumer<*>.cartItemsFragment(basket: CustomerBasket) {
    div(classes = "cart-items") {
        id = "cart-items"
        if (basket.items.isEmpty()) {
            cartEmpty()
        } else {
            basket.items.forEach { item ->
                cartItemCard(item)
            }
        }
    }
}

// Empty cart state
private fun TagConsumer<*>.cartEmpty() {
    div(classes = "cart-empty") {
        h2 { +"Your cart is empty" }
        p { +"Looks like you haven't added any items yet." }
        a(href = "/", classes = "button primary") { +"Browse Menu" }
    }
}

// Individual cart item card
private fun TagConsumer<*>.cartItemCard(item: BasketItem) {
    div(classes = "cart-item") {
        id = "cart-item-${item.id}"
        img(src = item.menuItemImageUrl, alt = item.menuItemName, classes = "cart-item-image")

        div(classes = "cart-item-details") {
            h3 { +item.menuItemName }
            p(classes = "cart-item-description") { +item.menuItemDescription }
            span(classes = "cart-item-price") {
                +"$${item.unitPrice.setScale(2, RoundingMode.HALF_UP).toPlainString()}"
            }
        }

        div(classes = "cart-item-actions") {
            // Quantity controls
            form(classes = "quantity-form") {
                attributes["hx-put"] = "/cart/items/${item.id}"
                attributes["hx-target"] = "#cart-items"
                attributes["hx-swap"] = "outerHTML"

                button(type = ButtonType.button, classes = "qty-btn") {
                    attributes["onclick"] = "this.nextElementSibling.stepDown(); this.form.requestSubmit();"
                    +"-"
                }
                numberInput(name = "quantity", classes = "quantity-input") {
                    value = item.quantity.toString()
                    min = "1"
                    max = "99"
                    attributes["onchange"] = "this.form.requestSubmit();"
                }
                button(type = ButtonType.button, classes = "qty-btn") {
                    attributes["onclick"] = "this.previousElementSibling.stepUp(); this.form.requestSubmit();"
                    +"+"
                }
            }

            // Item subtotal
            span(classes = "cart-item-subtotal") {
                val subtotal = item.unitPrice.multiply(BigDecimal(item.quantity))
                    .setScale(2, RoundingMode.HALF_UP)
                    .toPlainString()
                +"$$subtotal"
            }

            // Remove button
            button(classes = "remove-btn") {
                attributes["hx-delete"] = "/cart/items/${item.id}"
                attributes["hx-target"] = "#cart-items"
                attributes["hx-swap"] = "outerHTML"
                attributes["hx-confirm"] = "Remove this item from your cart?"
                +"Remove"
            }
        }
    }
}

// Cart summary (totals)
private fun TagConsumer<*>.cartSummary(basket: CustomerBasket, isOob: Boolean = false) {
    div(classes = "cart-summary") {
        id = "cart-summary"
        if (isOob) attributes["hx-swap-oob"] = "true"
        h2 { +"Order Summary" }

        if (basket.items.isEmpty()) {
            p { +"Your cart is empty" }
        } else {
            val subtotal = basket.items.fold(BigDecimal.ZERO) { acc, item ->
                acc + item.unitPrice.multiply(BigDecimal(item.quantity))
            }.setScale(2, RoundingMode.HALF_UP)

            div(classes = "summary-row") {
                span { +"Subtotal (${basket.items.sumOf { it.quantity }} items)" }
                span { +"$$subtotal" }
            }

            div(classes = "summary-row total") {
                span { +"Total" }
                span { +"$$subtotal" }
            }

            div(classes = "cart-actions") {
                a(href = "/", classes = "button secondary") { +"Continue Shopping" }
                a(href = "/checkout", classes = "button primary") { +"Proceed to Checkout" }
            }

            button(classes = "clear-cart-btn") {
                attributes["hx-delete"] = "/cart"
                attributes["hx-target"] = "#cart-items"
                attributes["hx-swap"] = "outerHTML"
                attributes["hx-confirm"] = "Clear your entire cart?"
                +"Clear Cart"
            }
        }
    }
}

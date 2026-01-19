package io.ktor.foodies.server.htmx.menu

import io.ktor.foodies.server.getValue
import io.ktor.foodies.server.htmx.MenuIntersectTrigger
import io.ktor.foodies.server.htmx.respondHtmxFragment
import io.ktor.foodies.server.security.UserSession
import io.ktor.server.application.Application
import io.ktor.server.htmx.hx
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.utils.io.ExperimentalKtorApi
import java.math.RoundingMode
import kotlin.collections.set
import kotlinx.html.ButtonType
import kotlinx.html.TagConsumer
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h3
import kotlinx.html.hiddenInput
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.p
import kotlinx.html.span

@OptIn(ExperimentalKtorApi::class)
fun Application.menuRoutes(menuService: MenuService) {
    routing {
        hx {
            get("/menu") {
                val offset: Int by call.parameters
                val limit: Int by call.parameters
                val items = menuService.menuItems(offset, limit)
                val isLoggedIn = call.sessions.get<UserSession>() != null
                call.respondHtmxFragment { buildMenuFragment(items, offset, limit, isLoggedIn) }
            }
        }
    }
}

private fun TagConsumer<Appendable>.buildMenuFragment(
    items: List<MenuItem>,
    offset: Int,
    limit: Int,
    isLoggedIn: Boolean,
) {
    val hasMore = items.size == limit
    val nextOffset = offset + items.size
    val statusMessage = if (hasMore) "" else "You reached the end of the menu."

    items.forEach { menuCard(it, isLoggedIn) }

    if (hasMore) {
        menuSentinel(nextOffset, limit)
    } else {
        feedComplete()
    }

    feedStatus(statusMessage)
}

private fun TagConsumer<Appendable>.menuCard(item: MenuItem, isLoggedIn: Boolean) {
    article(classes = "menu-card") {
        img(src = item.imageUrl, alt = item.name)

        div(classes = "content") {
            h3 { +item.name }
            p { +item.description }

            div(classes = "footer") {
                val formattedPrice = item.price.setScale(2, RoundingMode.HALF_UP).toPlainString()
                span(classes = "price") { +"$${formattedPrice}" }
                addToCartButton(item.id, isLoggedIn)
            }
        }
    }
}

private fun TagConsumer<Appendable>.addToCartButton(menuItemId: Long, isLoggedIn: Boolean) {
    if (isLoggedIn) {
        form(classes = "add-to-cart-form") {
            attributes["hx-post"] = "/cart/items"
            attributes["hx-swap"] = "none"
            hiddenInput(name = "menuItemId") { value = menuItemId.toString() }
            hiddenInput(name = "quantity") { value = "1" }
            button(type = ButtonType.submit, classes = "add-to-cart-btn") { +"Add to Cart" }
        }
    } else {
        a(href = "/login", classes = "add-to-cart-btn login-required") { +"Login to Order" }
    }
}

private fun TagConsumer<Appendable>.menuSentinel(nextOffset: Int, limit: Int) {
    div(classes = "sentinel") {
        id = "feed-sentinel"
        attributes["hx-get"] = "/menu?offset=$nextOffset&limit=$limit"
        attributes["hx-trigger"] = MenuIntersectTrigger
        attributes["hx-swap"] = "outerHTML"
        attributes["hx-indicator"] = "#feed-spinner"
        span { +"Loading more dishes..." }
    }
}

private fun TagConsumer<Appendable>.feedComplete() {
    div(classes = "sentinel sentinel-complete") {}
}

private fun TagConsumer<Appendable>.feedStatus(message: String) {
    span {
        id = "feed-status"
        attributes["hx-swap-oob"] = "true"
        attributes["role"] = "status"
        attributes["aria-live"] = "polite"
        +message
    }
}

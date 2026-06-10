package io.ktor.foodies.webapp.menu

import io.ktor.foodies.server.*
import io.ktor.foodies.webapp.*
import io.ktor.server.auth.oidc.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.htmx.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.html.*
import java.math.RoundingMode

const val DefaultMenuPageSize = 12
const val MenuIntersectTrigger = "intersect once"

@OptIn(ExperimentalKtorApi::class)
fun Route.menuRoutes(menuService: MenuService, provider: OidcProvider<OidcToken.Id>) {
    authenticateWith(provider.sessions.optional()) {
        val ctx = authenticatedContext()

        hx {
            get("/menu") {
                val offset: Int by call.parameters
                val limit: Int by call.parameters
                val items = menuService.menuItems(offset, limit)
                val isLoggedIn = with(ctx) { principal } != null
                call.respondHtmxFragment { buildMenuFragment(items, offset, limit, isLoggedIn) }
            }
        }
    }
}

private fun TagConsumer<Appendable>.buildMenuFragment(
    items: List<MenuItem>,
    offset: Int,
    limit: Int,
    isLoggedIn: Boolean
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
        img(src = item.imageUrl, alt = item.name) {
            loading = ImgLoading.lazy
        }

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
            button(type = ButtonType.submit, classes = "add-to-cart-btn") {
                +"Add to Cart"
            }
        }
    } else {
        a(href = "/login", classes = "add-to-cart-btn login-required") {
            +"Login to Order"
        }
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

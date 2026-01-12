package io.ktor.foodies.server.menu

import io.ktor.foodies.server.MenuIntersectTrigger
import io.ktor.foodies.server.UserSession
import io.ktor.foodies.server.respondHtmxFragment
import io.ktor.foodies.server.respondHtmlWithLayout
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.htmx.hx
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.util.getOrFail
import io.ktor.server.response.respond
import io.ktor.utils.io.ExperimentalKtorApi
import java.math.RoundingMode
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.TagConsumer
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h3
import kotlinx.html.hiddenInput
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.p
import kotlinx.html.span
import kotlin.collections.set

@OptIn(ExperimentalKtorApi::class)
fun Application.menuRoutes(menuService: MenuService) {
    routing {
        hx {
            get("/menu") {
                val search = call.request.queryParameters["search"]
                val isLoggedIn = call.sessions.get<UserSession>() != null

                if (search != null) {
                    val items = menuService.searchMenuItems(search)
                    call.respondHtmxFragment {
                        items.forEach { menuCard(it, isLoggedIn) }
                        if (items.isEmpty()) {
                            div(classes = "no-results") { +"No results found for \"$search\"" }
                        }
                        feedComplete()
                        feedStatus("")
                    }
                } else {
                    val offset = call.request.queryParameters.getOrFail<Int>("offset")
                    val limit = call.request.queryParameters.getOrFail<Int>("limit")
                    val items = menuService.menuItems(offset, limit)
                    call.respondHtmxFragment { buildMenuFragment(items, offset, limit, isLoggedIn) }
                }
            }
        }

        authenticate(optional = true) {
            get("/menu/{id}") {
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest)

                val item = menuService.getMenuItem(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound)

                val isLoggedIn = call.sessions.get<UserSession>() != null
                call.respondHtmlWithLayout(item.name, isLoggedIn) {
                    menuItemDetailPage(item, isLoggedIn)
                }
            }
        }
    }
}

private fun FlowContent.menuItemDetailPage(item: MenuItem, isLoggedIn: Boolean) {
    div(classes = "menu-detail") {
        a(href = "/", classes = "back-link") { +"← Back to Menu" }

        div(classes = "detail-grid") {
            img(src = item.imageUrl, alt = item.name, classes = "detail-image")

            div(classes = "detail-content") {
                h1 { +item.name }
                p(classes = "description") { +item.description }

                div(classes = "detail-footer") {
                    val formattedPrice = item.price.setScale(2, RoundingMode.HALF_UP).toPlainString()
                    span(classes = "price") { +"$${formattedPrice}" }
                    consumer.addToCartButton(item.id, isLoggedIn)
                }
            }
        }
    }
}

private fun TagConsumer<*>.buildMenuFragment(
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

private fun TagConsumer<*>.menuCard(item: MenuItem, isLoggedIn: Boolean) {
    article(classes = "menu-card") {
        a(href = "/menu/${item.id}") {
            img(src = item.imageUrl, alt = item.name)
        }

        div(classes = "content") {
            a(href = "/menu/${item.id}") {
                h3 { +item.name }
            }
            p { +item.description }

            div(classes = "footer") {
                val formattedPrice = item.price.setScale(2, RoundingMode.HALF_UP).toPlainString()
                span(classes = "price") { +"$${formattedPrice}" }
                addToCartButton(item.id, isLoggedIn)
            }
        }
    }
}

private fun TagConsumer<*>.addToCartButton(menuItemId: Long, isLoggedIn: Boolean) {
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

private fun TagConsumer<*>.menuSentinel(nextOffset: Int, limit: Int) {
    div(classes = "sentinel") {
        id = "feed-sentinel"
        attributes["hx-get"] = "/menu?offset=$nextOffset&limit=$limit"
        attributes["hx-trigger"] = MenuIntersectTrigger
        attributes["hx-swap"] = "outerHTML"
        attributes["hx-indicator"] = "#feed-spinner"
        span { +"Loading more dishes..." }
    }
}

private fun TagConsumer<*>.feedComplete() {
    div(classes = "sentinel sentinel-complete") {}
}

private fun TagConsumer<*>.feedStatus(message: String) {
    span {
        id = "feed-status"
        attributes["hx-swap-oob"] = "true"
        attributes["role"] = "status"
        attributes["aria-live"] = "polite"
        +message
    }
}

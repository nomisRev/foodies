package io.ktor.foodies.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.id
import kotlinx.html.lang
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.script
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.title

const val DefaultMenuPageSize = 12
const val MenuIntersectTrigger = "intersect once rootMargin: 800px"

fun Route.home() = get("/") {
    val isLoggedIn = call.sessions.get<UserSession>() != null

    call.respondHtmlWithLayout("Foodies - Discover the menu", isLoggedIn) {
        section(classes = "hero") {
            h1 { +"Your favorite dishes, one click away." }

            div(classes = "menu-grid") {
                id = "menu-feed"

                div(classes = "sentinel") {
                    id = "feed-sentinel"
                    attributes["hx-get"] = "/menu?offset=0&limit=$DefaultMenuPageSize"
                    attributes["hx-trigger"] = MenuIntersectTrigger
                    attributes["hx-swap"] = "outerHTML"
                    attributes["hx-indicator"] = "#feed-spinner"
                    span { +"Loading menu..." }
                }
            }

            div(classes = "feed-status") {
                span {
                    id = "feed-status"
                    attributes["role"] = "status"
                    attributes["aria-live"] = "polite"
                }
                div(classes = "spinner htmx-indicator") { id = "feed-spinner" }
            }
        }
    }
}

/**
 * Cart badge link that loads the item count via HTMX.
 * The badge count is loaded asynchronously to avoid blocking page load.
 */
fun FlowContent.cartBadgeLink() {
    a(href = "/cart", classes = "cart-link") {
        id = "cart-badge"
        attributes["hx-get"] = "/cart/badge"
        attributes["hx-trigger"] = "load, cart-updated from:body"
        attributes["hx-swap"] = "outerHTML"
        span(classes = "cart-icon") { +"Cart" }
    }
}

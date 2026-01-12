package io.ktor.foodies.server

import io.ktor.foodies.server.menu.MenuService
import io.ktor.foodies.server.menu.Category
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

fun Route.home(menuService: MenuService) = get("/") {
    val isLoggedIn = call.sessions.get<UserSession>() != null
    val categoryId = call.request.queryParameters["category"]?.toLongOrNull()
    val categories = menuService.getCategories()

    call.respondHtmlWithLayout("Foodies - Discover the menu", isLoggedIn) {
        section(classes = "hero") {
            h1 { +"Your favorite dishes, one click away." }

            div(classes = "menu-container") {
                categoriesSidebar(categories, categoryId)

                div(classes = "menu-grid-container") {
                    div(classes = "menu-grid") {
                        id = "menu-feed"

                        div(classes = "sentinel") {
                            id = "feed-sentinel"
                            val categoryParam = if (categoryId != null) "&category=$categoryId" else ""
                            attributes["hx-get"] = "/menu?offset=0&limit=$DefaultMenuPageSize$categoryParam"
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
    }
}

private fun FlowContent.categoriesSidebar(categories: List<Category>, activeCategoryId: Long?) {
    div(classes = "categories-sidebar") {
        a(href = "/", classes = "category-link ${if (activeCategoryId == null) "active" else ""}") {
            +"All"
        }
        categories.forEach { category ->
            a(href = "/?category=${category.id}", classes = "category-link ${if (activeCategoryId == category.id) "active" else ""}") {
                +category.name
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

package io.ktor.foodies.server.menu

import io.ktor.foodies.server.MenuIntersectTrigger
import io.ktor.foodies.server.respondHtmxFragment
import io.ktor.server.application.Application
import io.ktor.server.htmx.hx
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.util.getOrFail
import io.ktor.utils.io.ExperimentalKtorApi
import java.math.RoundingMode
import kotlinx.html.TagConsumer
import kotlinx.html.article
import kotlinx.html.div
import kotlinx.html.h3
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
                val offset = call.request.queryParameters.getOrFail<Int>("offset")
                val limit = call.request.queryParameters.getOrFail<Int>("limit")
                val items = menuService.menuItems(offset, limit)
                call.respondHtmxFragment { buildMenuFragment(items, offset, limit) }
            }
        }
    }
}

private fun TagConsumer<Appendable>.buildMenuFragment(items: List<MenuItem>, offset: Int, limit: Int) {
    val hasMore = items.size == limit
    val nextOffset = offset + items.size
    val statusMessage = if (hasMore) "" else "You reached the end of the menu."

    items.forEach { menuCard(it) }

    if (hasMore) {
        menuSentinel(nextOffset, limit)
    } else {
        feedComplete()
    }

    feedStatus(statusMessage)
}

private fun TagConsumer<Appendable>.menuCard(item: MenuItem) {
    article(classes = "menu-card") {
        img(src = item.imageUrl, alt = item.name)

        div(classes = "content") {
            h3 { +item.name }
            p { +item.description }

            div(classes = "footer") {
                val formattedPrice = item.price.setScale(2, RoundingMode.HALF_UP).toPlainString()
                span(classes = "price") { +"$${formattedPrice}" }
                span(classes = "tag") { +"Popular" }
            }
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

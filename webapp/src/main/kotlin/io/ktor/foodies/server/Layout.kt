package io.ktor.foodies.server

import io.ktor.server.html.respondHtml
import io.ktor.server.application.ApplicationCall
import io.ktor.http.HttpStatusCode
import kotlinx.html.*

suspend fun ApplicationCall.respondHtmlWithLayout(
    pageTitle: String,
    isLoggedIn: Boolean,
    extraStyles: List<String> = emptyList(),
    pageContent: FlowContent.() -> Unit
) {
    respondHtml(HttpStatusCode.OK) {
        lang = "en"
        head {
            meta { charset = "utf-8" }
            meta { name = "viewport"; content = "width=device-width, initial-scale=1" }
            title { +pageTitle }
            link(rel = "stylesheet", href = "/static/home.css")
            extraStyles.forEach { style ->
                link(rel = "stylesheet", href = style)
            }
            script(src = "https://unpkg.com/htmx.org@1.9.12") {}
        }
        body {
            header {
                a(href = "/", classes = "logo") { +"Foodies" }
                div(classes = "search-container") {
                    input(type = InputType.search, classes = "search-input") {
                        name = "search"
                        placeholder = "Search menu..."
                        attributes["hx-get"] = "/menu"
                        attributes["hx-trigger"] = "keyup changed delay:300ms"
                        attributes["hx-target"] = "#menu-feed"
                        attributes["hx-swap"] = "innerHTML"
                        attributes["hx-indicator"] = "#search-spinner"
                    }
                    div(classes = "spinner htmx-indicator") { id = "search-spinner" }
                }
                div(classes = "actions") {
                    cartBadgeLink()
                    if (isLoggedIn) {
                        a(href = "/logout", classes = "button secondary") { +"Log out" }
                    } else {
                        a(href = "/login", classes = "button primary") { +"Log in" }
                    }
                }
            }
            main {
                div(classes = "container") {
                    pageContent()
                }
            }
            // Toast container for notifications
            div(classes = "toast-container") {
                div { id = "toast" }
            }
        }
    }
}

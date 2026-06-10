package io.ktor.foodies.webapp.home

import io.ktor.foodies.webapp.basket.*
import io.ktor.foodies.webapp.menu.*
import io.ktor.http.*
import io.ktor.server.auth.oidc.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.html.*

fun Route.homeRoutes(provider: OidcProvider<OidcToken.Id>) {
    staticResources("/static", "static")
    home(provider)
}

@OptIn(ExperimentalKtorApi::class)
fun Route.home(provider: OidcProvider<OidcToken.Id>) = authenticateWith(provider.sessions.optional()) {
    val ctx = authenticatedContext()

    get("/") {
        val userOrNull = with(ctx) { principal }
        val isLoggedIn = userOrNull != null

        call.respondHtml(HttpStatusCode.OK) {
            lang = "en"

            head {
                meta { charset = "utf-8" }
                meta { name = "viewport"; content = "width=device-width, initial-scale=1" }
                title { +"Foodies - Discover the menu" }
                link(rel = "stylesheet", href = "/static/home.css")
                script(src = "https://unpkg.com/htmx.org@1.9.12") {}
            }

            body {
                header {
                    a(href = "/", classes = "logo") { +"Foodies" }
                    div(classes = "actions") {
                        basketBadgeLink()
                        if (isLoggedIn) {
                            form(action = "/logout", method = FormMethod.post, classes = "logout-form") {
                                button(type = ButtonType.submit, classes = "button secondary") { +"Log out" }
                            }
                        } else {
                            a(href = "/login", classes = "button primary") { +"Log in" }
                        }
                    }
                }

                main {
                    section(classes = "hero") {
                        h1 { +"Your favorite dishes, one click away." }


                        div(classes = "menu-grid") {
                            id = "menu-feed"

                            div(classes = "sentinel") {
                                id = "feed-sentinel"
                                attributes["hx-get"] = "/menu?offset=0&limit=${DefaultMenuPageSize}"
                                attributes["hx-trigger"] =
                                    MenuIntersectTrigger
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

                div(classes = "toast-container") {
                    div { id = "toast" }
                }
            }
        }
    }
}

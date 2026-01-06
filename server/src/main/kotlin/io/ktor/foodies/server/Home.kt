package io.ktor.foodies.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.lang
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.title

fun Route.home() = get("/") {
    val isLoggedIn = call.sessions.get<UserSession>() != null

    call.respondHtml(HttpStatusCode.OK) {
        lang = "en"

        head {
            meta { charset = "utf-8" }
            meta { name = "viewport"; content = "width=device-width, initial-scale=1" }
            title { +"Foodies" }
        }

        body {
            h1 { +"Foodies" }
            p { +(if (isLoggedIn) "You are signed in." else "You are signed out.") }

            div {
                if (isLoggedIn) {
                    a(href = "/logout") { +"Log out" }
                } else {
                    a(href = "/login") { +"Log in" }
                }
            }
        }
    }
}
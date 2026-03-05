package io.ktor.foodies.server.home

import io.ktor.server.application.Application
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.routing

fun Application.homeModule() {
    routing {
        staticResources("/static", "static")
        home()
    }
}

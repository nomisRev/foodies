package io.ktor.foodies.server

import io.ktor.foodies.server.menu.InMemoryMenuService
import io.ktor.foodies.server.menu.menuRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    val env = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = env.host, port = env.port) {
        security(env.security)
        app()
    }.start(wait = true)
}

fun Application.app() {
    val menuService = InMemoryMenuService()

    routing {
        staticResources("/static", "static")
        home()
        menuRoutes(menuService)
        healthz()
    }
}

fun Route.healthz() = get("/healthz") { call.respond(HttpStatusCode.OK) }

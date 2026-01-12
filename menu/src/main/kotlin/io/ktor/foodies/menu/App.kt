package io.ktor.foodies.menu

import io.ktor.foodies.server.ValidationException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        app(module(config))
    }.start(wait = true)
}

fun Application.app(module: MenuModule) {
    install(ContentNegotiation) { json() }

    install(StatusPages) {
        exception<ValidationException> { call, cause ->
            call.respondText(cause.message, status = HttpStatusCode.BadRequest)
        }
    }

    routing {
        healthz()
        menuRoutes(module.menuService)
    }
}

fun Route.healthz() = get("/healthz") { call.respond(HttpStatusCode.OK) }

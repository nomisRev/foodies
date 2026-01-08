package io.ktor.foodies.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.launchIn

fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        app(module(config))
    }.start(wait = true)
}

fun Application.app(module: Module) {
    module.consumers.forEach { it.process().launchIn(this) }

    routing {
        healthz()
    }
}

fun Route.healthz() = get("/healthz") { call.respond(HttpStatusCode.OK) }

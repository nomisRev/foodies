package io.ktor.app.io.ktor.foodies.server

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

fun main() {
    val env = ApplicationConfig("application.yaml").property("env").getAs<Config>()
    embeddedServer(Netty, host = env.host, port = env.port) {
            val module = Module(database(env.dataSource))
            app(module)
        }
        .start(wait = true)
}

fun Application.app(module: Module) {
    routing { healthz() }
}

fun Route.healthz() = get("/healthz") { call.respond(HttpStatusCode.OK) }

package io.ktor.foodies.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.html.respondHtml
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.head
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.header
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.title
import kotlinx.html.lang

fun main() {
    val env = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = env.host, port = env.port) {
        val module = Module(database(env.dataSource))
        security(env.security)
        app(module)
    }.start(wait = true)
}

fun Application.app(module: Module) {
    routing {
        staticResources("/static", "static")
        home()
        healthz()
    }
}

fun Route.healthz() = get("/healthz") { call.respond(HttpStatusCode.OK) }

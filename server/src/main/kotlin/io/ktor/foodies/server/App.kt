package io.ktor.app.io.ktor.foodies.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
data class Env(val host: String, val port: Int)

fun main() {
    val env = ApplicationConfig("application.yaml").property("env").getAs<Env>()
    embeddedServer(Netty, host = env.host, port = env.port) {
        app()
    }
}

fun Application.app() {
    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK)
        }
    }
}

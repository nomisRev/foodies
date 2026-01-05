package io.ktor.app

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    embeddedServer(Netty, host = "localhost", port = 8080) {
        routing {
            get("/") {
                call.respondText("Hello World!")
            }
        }
    }
}

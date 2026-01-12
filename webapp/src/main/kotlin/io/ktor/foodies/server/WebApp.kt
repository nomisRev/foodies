package io.ktor.foodies.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.foodies.server.basket.HttpBasketService
import io.ktor.foodies.server.cart.cartRoutes
import io.ktor.foodies.server.menu.HttpMenuService
import io.ktor.foodies.server.menu.menuRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
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
        val httpClient = HttpClient(Apache5) {
            install(ContentNegotiation) { json() }
        }
        monitor.subscribe(ApplicationStopped) { httpClient.close() }

        security(env.security, httpClient)
        app(env, httpClient)
    }.start(wait = true)
}

fun Application.app(config: Config, httpClient: HttpClient) {
    val menuService = HttpMenuService(config.menu.baseUrl, httpClient)
    val basketService = HttpBasketService(config.basket.baseUrl, httpClient)

    menuRoutes(menuService)
    cartRoutes(basketService)

    routing {
        staticResources("/static", "static")
        home()
        healthz()
    }
}

fun Route.healthz() = get("/healthz") { call.respond(HttpStatusCode.OK) }

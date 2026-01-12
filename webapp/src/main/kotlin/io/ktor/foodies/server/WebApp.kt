package io.ktor.foodies.server

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.healthcheck.http.EndpointHealthCheck
import com.sksamuel.cohort.threads.ThreadDeadlockHealthCheck
import io.ktor.client.request.get
import com.sksamuel.cohort.HealthCheckRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.foodies.server.basket.HttpBasketService
import io.ktor.foodies.server.cart.cartRoutes
import io.ktor.foodies.server.menu.HttpMenuService
import io.ktor.foodies.server.menu.menuRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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

    install(Cohort) {
        verboseHealthCheckResponse = true

        healthcheck("/healthz/startup", HealthCheckRegistry(Dispatchers.Default))
        healthcheck("/healthz/liveness", HealthCheckRegistry(Dispatchers.Default) {
            register(ThreadDeadlockHealthCheck(), Duration.ZERO, 1.minutes)
        })
        healthcheck("/healthz/readiness", HealthCheckRegistry(Dispatchers.IO) {
            register("menu-service", EndpointHealthCheck { it.get("${config.menu.baseUrl}/healthz/readiness") })
            register("basket-service", EndpointHealthCheck { it.get("${config.basket.baseUrl}/healthz/readiness") })
        })
    }

    menuRoutes(menuService)
    cartRoutes(basketService)

    routing {
        staticResources("/static", "static")
        home()
    }
}

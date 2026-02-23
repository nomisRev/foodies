package io.ktor.foodies.server

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import io.ktor.foodies.server.htmx.cart.cartRoutes
import io.ktor.foodies.server.htmx.home
import io.ktor.foodies.server.htmx.menu.menuRoutes
import io.ktor.foodies.server.security.security
import io.ktor.foodies.server.telemetry.monitoring
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.plus
import io.ktor.server.routing.routing
import io.ktor.server.routing.routingRoot
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.coroutines.Dispatchers

fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        val (_, openTelemetry) = monitoring(config.telemetry)
        app(config, module(config, openTelemetry))
    }.start(wait = true)
}

@OptIn(ExperimentalKtorApi::class)
suspend fun Application.app(config: Config, module: WebAppModule) {
    install(ContentNegotiation) { json() }
    install(Cohort) {
        verboseHealthCheckResponse = true

        healthcheck("/healthz/startup", HealthCheckRegistry(Dispatchers.Default))
        healthcheck("/healthz/liveness", HealthCheckRegistry(Dispatchers.Default))
        healthcheck("/healthz/readiness", module.readinessCheck)
    }

    security(config.security, module.httpClient, module.sessionStorage)

    menuRoutes(module.menuService)
    cartRoutes(module.basketService)

    routing {
        staticResources("/static", "static")
        home()
        get("/api/openapi.json") {
            val doc =
                OpenApiDoc(info = OpenApiInfo("Foodies Webapp API", "1.0")) +
                    call.application.routingRoot.descendants()
            call.respond(doc)
        }
    }
}

package io.ktor.foodies.webapp

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import io.ktor.foodies.webapp.basket.basketRoutes
import io.ktor.foodies.webapp.home.homeRoutes
import io.ktor.foodies.webapp.menu.menuRoutes
import io.ktor.foodies.webapp.security.security
import io.ktor.foodies.server.telemetry.monitoring
import io.ktor.foodies.webapp.app
import io.ktor.foodies.webapp.basket.basketRoutes
import io.ktor.foodies.webapp.home.homeRoutes
import io.ktor.foodies.webapp.menu.menuRoutes
import io.ktor.foodies.webapp.module
import io.ktor.foodies.webapp.security.security
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers

fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<io.ktor.foodies.webapp.Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        val (_, openTelemetry) = monitoring(config.telemetry)
        app(config, module(config, openTelemetry))
    }.start(wait = true)
}

suspend fun Application.app(config: io.ktor.foodies.webapp.Config, module: io.ktor.foodies.webapp.WebAppModule) {
    install(ContentNegotiation) { json() }
    install(Cohort) {
        verboseHealthCheckResponse = true

        healthcheck("/healthz/startup", HealthCheckRegistry(Dispatchers.Default))
        healthcheck("/healthz/liveness", HealthCheckRegistry(Dispatchers.Default))
        healthcheck("/healthz/readiness", module.readinessCheck)
    }

    security(config.security, module.httpClient, module.sessionStorage)

    routing {
        homeRoutes()
        menuRoutes(module.menuService)
        basketRoutes(module.basketService)
    }
}

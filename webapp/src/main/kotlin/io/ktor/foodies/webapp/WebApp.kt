package io.ktor.foodies.webapp

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import io.ktor.foodies.server.telemetry.*
import io.ktor.foodies.webapp.basket.*
import io.ktor.foodies.webapp.home.*
import io.ktor.foodies.webapp.menu.*
import io.ktor.foodies.webapp.security.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers

fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        val (_, openTelemetry) = monitoring(config.telemetry)
        app(config, module(config, openTelemetry))
    }.start(wait = true)
}

suspend fun Application.app(config: Config, module: WebAppModule) {
    install(ContentNegotiation) { json() }
    install(Cohort) {
        verboseHealthCheckResponse = true

        healthcheck("/healthz/startup", HealthCheckRegistry(Dispatchers.Default))
        healthcheck("/healthz/liveness", HealthCheckRegistry(Dispatchers.Default))
        healthcheck("/healthz/readiness", module.readinessCheck)
    }

    val provider = security(config.security, module.httpClient, module.sessionStorage)

    routing {
        homeRoutes(provider)
        menuRoutes(module.menuService, provider)
        basketRoutes(module.basketService, provider)
    }
}

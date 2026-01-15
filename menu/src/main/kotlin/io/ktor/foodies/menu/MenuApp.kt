package io.ktor.foodies.menu

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.HealthCheckRegistry.Companion.invoke
import io.ktor.foodies.server.ValidationException
import io.ktor.foodies.server.telemetry.openTelemetry
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers

fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        app(module(config))
    }.start(wait = true)
}

fun Application.app(module: MenuModule) {
    openTelemetry(serviceName = "menu-service", serviceVersion = "1.0.0")

    install(ContentNegotiation) { json() }

    install(StatusPages) {
        exception<ValidationException> { call, cause ->
            call.respondText(cause.message, status = HttpStatusCode.BadRequest)
        }
    }

    install(Cohort) {
        verboseHealthCheckResponse = true
        healthcheck("/healthz/startup", HealthCheckRegistry(Dispatchers.Default))
        healthcheck("/healthz/liveness", HealthCheckRegistry(Dispatchers.Default))
        healthcheck("/healthz/readiness", module.readinessCheck)
    }

    routing {
        menuRoutes(module.menuService)
    }
}

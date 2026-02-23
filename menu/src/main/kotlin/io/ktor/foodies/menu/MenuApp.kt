package io.ktor.foodies.menu

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import io.ktor.foodies.server.ValidationException
import io.ktor.foodies.server.telemetry.monitoring
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.plus
import io.ktor.server.routing.routing
import io.ktor.server.routing.routingRoot
import io.ktor.utils.io.ExperimentalKtorApi
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn

fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        val (_, openTelemetry) = monitoring(config.telemetry)
        app(module(config, openTelemetry))
    }.start(wait = true)
}

@OptIn(ExperimentalKtorApi::class)
fun Application.app(module: MenuModule) {
    install(ContentNegotiation) { json() }

    module.consumers.forEach { it.launchIn(this) }

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
        get("/api/openapi.json") {
            val doc =
                OpenApiDoc(info = OpenApiInfo("Foodies Menu API", "1.0")) +
                    call.application.routingRoot.descendants()
            call.respond(doc)
        }
    }
}

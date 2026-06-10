package io.ktor.foodies.basket

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import io.ktor.foodies.server.*
import io.ktor.foodies.server.openid.*
import io.ktor.foodies.server.telemetry.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn

fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        security(config.keycloak)
        val monitoring = openTelemetry(config.telemetry.otlpEndpoint)
        app(module(config, monitoring))
    }.start(wait = true)
}

@OptIn(ExperimentalLettuceCoroutinesApi::class)
fun Application.app(module: BasketModule) {
    install(ContentNegotiation) { json() }

    install(StatusPages) {
        exception<ValidationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, cause.reasons.joinToString("\n"))
        }
    }

    module.consumers.forEach { it.launchIn(this) }

    routing {
        install(Cohort) {
            verboseHealthCheckResponse = true
            healthcheck("/healthz/startup", HealthCheckRegistry(Dispatchers.Default))
            healthcheck("/healthz/liveness", HealthCheckRegistry(Dispatchers.Default))
            healthcheck("/healthz/readiness", module.readinessCheck)
        }
        basketRoutes(module.basketService)
    }
}

package io.ktor.foodies.basket

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.foodies.server.ValidationException
import io.ktor.foodies.server.openid.discover
import io.ktor.foodies.server.telemetry.openTelemetry
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn

fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        security(config)
        app(module(config))
    }.start(wait = true)
}

@OptIn(ExperimentalLettuceCoroutinesApi::class)
fun Application.app(module: BasketModule) {
    openTelemetry(serviceName = "basket-service", serviceVersion = "1.0.0")

    install(ContentNegotiation) { json() }

    install(StatusPages) {
        exception<ValidationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, cause.reasons.joinToString("\n"))
        }
    }

    module.consumers.forEach { it.process().launchIn(this) }

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

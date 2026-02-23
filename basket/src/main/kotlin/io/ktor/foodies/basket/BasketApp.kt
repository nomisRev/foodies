package io.ktor.foodies.basket

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import io.ktor.foodies.server.ValidationException
import io.ktor.foodies.server.openid.security
import io.ktor.foodies.server.telemetry.openTelemetry
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
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.plus
import io.ktor.server.routing.routing
import io.ktor.server.routing.routingRoot
import io.ktor.utils.io.ExperimentalKtorApi
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn

fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        security(config.auth)
        val monitoring = openTelemetry(config.telemetry.otlpEndpoint)
        app(module(config, monitoring))
    }.start(wait = true)
}

@OptIn(ExperimentalLettuceCoroutinesApi::class, ExperimentalKtorApi::class)
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
        get("/api/openapi.json") {
            val doc =
                OpenApiDoc(info = OpenApiInfo("Foodies Basket API", "1.0")) +
                    call.application.routingRoot.descendants()
            call.respond(doc)
        }
    }
}

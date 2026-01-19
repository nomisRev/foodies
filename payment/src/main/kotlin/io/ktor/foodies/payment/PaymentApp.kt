package io.ktor.foodies.payment

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import io.ktor.foodies.server.telemetry.openTelemetry
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn

fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
            val (_, openTelemetry) = openTelemetry(config.telemetry.otlpEndpoint)
            app(module(config, openTelemetry))
        }
        .start(wait = true)
}

fun Application.app(module: PaymentModule) {
    install(ContentNegotiation) { json() }

    module.consumers.forEach { it.launchIn(this) }

    install(Cohort) {
        verboseHealthCheckResponse = true
        healthcheck("/healthz/startup", HealthCheckRegistry(Dispatchers.Default))
        healthcheck("/healthz/liveness", HealthCheckRegistry(Dispatchers.Default))
        healthcheck("/healthz/readiness", module.readinessCheck)
    }

    routing { paymentRoutes(module.paymentService) }
}

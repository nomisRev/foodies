package io.ktor.foodies.server

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import io.ktor.foodies.server.openid.security
import io.ktor.foodies.server.telemetry.openTelemetry
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
import kotlinx.coroutines.flow.launchIn

fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        security(config.auth)
        val (_, openTelemetry) = openTelemetry(config.telemetry.otlpEndpoint)
        app(config, module(config, openTelemetry))
    }.start(wait = true)
}

fun Application.app(config: Config, module: ProfileModule) {
    install(ContentNegotiation) { json() }

    module.consumers.forEach { it.launchIn(this) }

    install(Cohort) {
        verboseHealthCheckResponse = true
        healthcheck("/healthz/startup", HealthCheckRegistry(Dispatchers.Default))
        healthcheck("/healthz/liveness", HealthCheckRegistry(Dispatchers.Default))
        healthcheck("/healthz/readiness", module.readinessCheck)
    }

    routing {
        profileRoutes(module.profileRepository)
    }
}

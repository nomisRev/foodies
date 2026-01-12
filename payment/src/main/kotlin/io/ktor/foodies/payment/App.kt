package io.ktor.foodies.payment

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.threads.ThreadDeadlockHealthCheck
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        app(module(config))
    }.start(wait = true)
}

fun Application.app(module: PaymentModule) {
    install(ContentNegotiation) { json() }

    routing {
        install(Cohort) {
            verboseHealthCheckResponse = true
            healthcheck("/healthz/startup", HealthCheckRegistry(Dispatchers.Default))
            healthcheck("/healthz/liveness", HealthCheckRegistry(Dispatchers.Default) {
                register(ThreadDeadlockHealthCheck(), Duration.ZERO, 1.minutes)
            })
            healthcheck("/healthz/readiness", module.readinessCheck)
        }
        paymentRoutes(module.paymentService)
    }
}

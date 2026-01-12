package io.ktor.foodies.basket

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.threads.ThreadDeadlockHealthCheck
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.foodies.server.ValidationException
import io.ktor.foodies.server.openid.discover
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        security(config)
        app(module(config))
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

    module.consumers.forEach { it.process().launchIn(this) }

    routing {
        install(Cohort) {
            verboseHealthCheckResponse = true
            healthcheck("/healthz/startup", HealthCheckRegistry(Dispatchers.Default))
            healthcheck("/healthz/liveness", HealthCheckRegistry(Dispatchers.Default) {
                register(ThreadDeadlockHealthCheck(), Duration.ZERO, 1.minutes)
            })
            healthcheck("/healthz/readiness", module.readinessCheck)
        }
        basketRoutes(module.basketService)
    }
}

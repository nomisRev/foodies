package io.ktor.foodies.server

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.threads.ThreadDeadlockHealthCheck
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        app(module(config))
    }.start(wait = true)
}

fun Application.app(module: ProfileModule) {
    module.consumers.forEach { it.process().launchIn(this) }

    routing {
        healthz()
    }
}

fun Route.healthz() = get("/healthz") { call.respond(HttpStatusCode.OK) }

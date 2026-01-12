package io.ktor.foodies.basket

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
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
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
    install(ContentNegotiation) { json() }

    install(StatusPages) {
        exception<ValidationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, cause.reasons.joinToString("\n"))
        }
    }

    module.consumers.forEach { it.process().launchIn(this) }

    routing {
        healthz()
        basketRoutes(module.basketService)
    }
}

fun Route.healthz() = get("/healthz") { call.respond(HttpStatusCode.OK) }

private suspend fun Application.security(config: Config) {
    val openIdConfig = HttpClient(CIO).use { it.discover(config.auth.issuer) }
    install(Authentication) {
        jwt {
            verifier(openIdConfig.jwksProvider(), config.auth.issuer)
            validate { credential ->
// TODO: Setup proper audience in keycloak
//                 Validate audience if configured
//                if (config.auth.audience.isNotBlank()) {
                if (!credential.payload.audience.contains(config.auth.audience)) {
                    return@validate null
                }
//                }
                credential.payload
            }
        }
    }
}
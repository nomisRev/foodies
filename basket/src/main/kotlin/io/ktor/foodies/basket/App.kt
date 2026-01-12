package io.ktor.foodies.basket

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.get
import io.ktor.foodies.server.ValidationException
import io.ktor.foodies.server.openid.discover
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
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
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import kotlinx.coroutines.flow.launchIn
import kotlinx.serialization.Serializable

fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        app(config, module(config))
    }.start(wait = true)
}

@OptIn(ExperimentalLettuceCoroutinesApi::class)
suspend fun Application.app(config: Config, module: BasketModule) {
    install(ContentNegotiation) { json() }

    install(StatusPages) {
        exception<ValidationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, cause.reasons.joinToString("\n"))
        }
    }

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

    // Launch event consumers
    module.consumers.forEach { it.process().launchIn(this) }

    routing {
        healthz()
        healthzReady(module.redisCommands)
        basketRoutes(module.basketService)
    }
}

fun Route.healthz() = get("/healthz") { call.respond(HttpStatusCode.OK) }

@OptIn(ExperimentalLettuceCoroutinesApi::class)
fun Route.healthzReady(redisCommands: RedisCoroutinesCommands<String, String>) = get("/healthz/ready") {
    val redisHealthy = runCatching { redisCommands.ping() }.isSuccess
    val statusCode = if (redisHealthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
    call.respond(statusCode, HealthStatus(status = if (redisHealthy) "UP" else "DOWN"))
}

@Serializable
data class HealthStatus(val status: String)


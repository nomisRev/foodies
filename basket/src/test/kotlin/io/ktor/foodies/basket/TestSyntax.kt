package io.ktor.foodies.basket

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.lettuce.core.ExperimentalLettuceCoroutinesApi

private const val TEST_SECRET = "test-jwt-secret-for-end-to-end-testing"
private const val TEST_ISSUER = "test-issuer"
private const val TEST_AUDIENCE = "test-audience"

/**
 * Configures test application with mock JWT authentication and real Redis.
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
private fun Application.testApp(module: BasketModule) {
    install(ContentNegotiation) { json() }

    install(Authentication) {
        jwt {
            verifier(
                JWT.require(Algorithm.HMAC256(TEST_SECRET))
                    .withIssuer(TEST_ISSUER)
                    .withAudience(TEST_AUDIENCE)
                    .build()
            )
            validate { credential ->
                if (credential.payload.subject != null) JWTPrincipal(credential.payload) else null
            }
        }
    }

    routing {
        get("/healthz") { call.respond(HttpStatusCode.OK) }
        healthzReady(module.redisCommands)
        basketRoutes(module.basketService)
    }
}

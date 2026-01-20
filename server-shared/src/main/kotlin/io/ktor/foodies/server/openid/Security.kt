package io.ktor.foodies.server.openid

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import kotlinx.serialization.Serializable

@Serializable
data class Auth(val issuer: String)

suspend fun Application.security(auth: Auth) {
    HttpClient(Apache5) {
        install(ContentNegotiation) { json() }
        install(HttpRequestRetry) {
            retryOnExceptionOrServerErrors(maxRetries = 5)
            exponentialDelay()
        }
    }.use { client -> security(auth, client) }
}

suspend fun Application.security(auth: Auth, client: HttpClient) {
    val config = client.use { it.discover(auth.issuer) }

    install(Authentication) {
        jwt {
            verifier(config.jwks(), config.issuer) {
                withAudience("foodies")
            }
            validate { credential -> JWTPrincipal(credential.payload) }
        }
    }
}

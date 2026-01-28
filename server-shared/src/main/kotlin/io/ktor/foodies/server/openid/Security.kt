package io.ktor.foodies.server.openid

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.foodies.server.auth.ServicePrincipal
import io.ktor.foodies.server.auth.UserPrincipal
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt

suspend fun Application.security(issuer: String) {
    HttpClient(Apache5) {
        install(ContentNegotiation) { json() }
        install(HttpRequestRetry) {
            retryOnExceptionOrServerErrors(maxRetries = 5)
            exponentialDelay()
        }
    }.use { client -> security(issuer, client) }
}

suspend fun Application.security(issuer: String, client: HttpClient) {
    val config = client.discover(issuer)
    val jwks = config.jwks()

    install(Authentication) {
        jwt("user") {
            verifier(jwks, config.issuer) {
                withAudience("foodies")
            }
            validate { credential ->
                val payload = credential.payload
                val email = payload.getClaim("email").asString()
                val authHeader = request.headers["Authorization"]?.removePrefix("Bearer ") ?: ""
                if (email != null) {
                    val roles = payload.getClaim("realm_access")
                        ?.asMap()?.get("roles") as? List<*> ?: emptyList<String>()
                    UserPrincipal(
                        userId = payload.subject,
                        email = email,
                        roles = roles.filterIsInstance<String>().toSet(),
                        accessToken = authHeader
                    )
                } else null
            }
        }

        jwt("service") {
            verifier(jwks, config.issuer) {
                withAudience("foodies")
            }
            validate { credential ->
                val payload = credential.payload
                val clientId = payload.getClaim("azp").asString()
                    ?: payload.getClaim("client_id").asString()
                if (clientId?.endsWith("-service") == true) {
                    val roles = payload.getClaim("realm_access")
                        ?.asMap()?.get("roles") as? List<*> ?: emptyList<String>()
                    ServicePrincipal(
                        serviceAccountId = payload.subject,
                        clientId = clientId,
                        roles = roles.filterIsInstance<String>().toSet()
                    )
                } else null
            }
        }
    }
}

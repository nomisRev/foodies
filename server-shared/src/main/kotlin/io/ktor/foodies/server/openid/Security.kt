package io.ktor.foodies.server.openid

import com.auth0.jwt.interfaces.Payload
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
import kotlinx.serialization.Serializable

@Serializable
data class Auth(
    val issuer: String,
    val audience: String = "foodies",
)

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
        jwt("user") {
            verifier(config.jwks(), config.issuer) { withAudience(auth.audience) }
            validate { credential ->
                val payload = credential.payload
                val email = payload.getClaim("email").asString()
                val authHeader = request.headers["Authorization"]?.removePrefix("Bearer ") ?: ""
                if (email != null) {
                    UserPrincipal(
                        userId = payload.subject,
                        email = email,
                        roles = payload.realmRoles(),
                        accessToken = authHeader
                    )
                } else null
            }
        }

        jwt("service") {
            verifier(config.jwks(), config.issuer) { withAudience(auth.audience) }
            validate { credential ->
                val payload = credential.payload
                val clientId = payload.getClaim("azp").asString()
                    ?: payload.getClaim("client_id").asString()
                if (clientId?.endsWith("-service") == true) {
                    ServicePrincipal(
                        serviceAccountId = payload.subject,
                        clientId = clientId,
                        roles = payload.resourceRoles(auth.audience)
                    )
                } else null
            }
        }
    }
}

private fun Payload.realmRoles(): Set<String> {
    val roles = getClaim("realm_access")?.asMap()?.get("roles") as? List<*>
    return roles?.filterIsInstance<String>()?.toSet() ?: emptySet()
}

private fun Payload.resourceRoles(audience: String): Set<String> {
    val resourceAccess = getClaim("resource_access")?.asMap()
    val roles = (resourceAccess?.get(audience) as? Map<*, *>)?.get("roles") as? List<*>
    return roles?.filterIsInstance<String>()?.toSet() ?: emptySet()
}

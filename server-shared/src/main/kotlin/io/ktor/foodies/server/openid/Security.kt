package io.ktor.foodies.server.openid

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Auth(
    val issuer: String,
    @SerialName("service_audience") val serviceAudience: String,
    @SerialName("user_audience") val userAudience: String = "account"
)

const val AUTH_USER = "auth-user"
const val AUTH_SERVICE = "auth-service"

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
        jwt(AUTH_USER) {
            verifier(config.jwks(), config.issuer) {
                withAudience(auth.userAudience)
            }
            validate { credential ->
                val sub = credential.payload.subject
                if (sub != null) {
                    UserPrincipal(
                        userId = sub,
                        email = credential.payload.getClaim("email")?.asString(),
                        name = credential.payload.getClaim("name")?.asString()
                            ?: credential.payload.getClaim("preferred_username")?.asString(),
                        roles = extractRoles(credential.payload.getClaim("realm_access")),
                        scopes = credential.payload.getClaim("scope")?.asString()
                            ?.split(" ")?.toSet() ?: emptySet()
                    )
                } else {
                    null
                }
            }
        }

        jwt(AUTH_SERVICE) {
            verifier(config.jwks(), config.issuer) {
                withAudience(auth.serviceAudience)
            }
            validate { credential ->
                val azp = credential.payload.getClaim("azp")?.asString()
                if (azp != null) {
                    ServicePrincipal(
                        serviceId = azp,
                        scopes = credential.payload.getClaim("scope")?.asString()?.split(" ")?.toSet() ?: emptySet()
                    )
                } else {
                    null
                }
            }
        }
    }
}

private fun extractRoles(realmAccessClaim: com.auth0.jwt.interfaces.Claim?): Set<String> {
    return try {
        @Suppress("UNCHECKED_CAST")
        val realmAccess = realmAccessClaim?.asMap() as? Map<String, Any>
        val roles = realmAccess?.get("roles") as? List<String>
        roles?.toSet() ?: emptySet()
    } catch (e: Exception) {
        emptySet()
    }
}

data class ServicePrincipal(
    val serviceId: String,
    val scopes: Set<String> = emptySet()
) {
    fun hasScope(scope: String): Boolean = scope in scopes
    fun hasAllScopes(vararg scopes: String): Boolean = scopes.all { it in this.scopes }
}

data class UserPrincipal(
    val userId: String,
    val email: String? = null,
    val name: String? = null,
    val roles: Set<String> = emptySet(),
    val scopes: Set<String> = emptySet()
) {
    fun hasRole(role: String): Boolean = role in roles
    fun hasScope(scope: String): Boolean = scope in scopes
    fun hasAllScopes(vararg scopes: String): Boolean = scopes.all { it in this.scopes }
    fun hasAnyScope(vararg scopes: String): Boolean = scopes.any { it in this.scopes }
}


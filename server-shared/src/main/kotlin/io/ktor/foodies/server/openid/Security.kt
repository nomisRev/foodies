package io.ktor.foodies.server.openid

import com.auth0.jwt.interfaces.Claim
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.route
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Auth(
    val issuer: String,
    @SerialName("service_audience") val serviceAudience: String,
    @SerialName("user_audience") val userAudience: String
)

const val AUTH_USER = "auth-user"
private const val AUTH_SERVICE = "auth-service"

/**
 * User-authenticated routes.
 * Ensures the request is authenticated with a valid user token.
 */
fun Route.authenticatedUser(build: Route.() -> Unit) = authenticate(AUTH_USER) {
    build()
}

/**
 * Service-authenticated routes.
 * Ensures the request is authenticated with a valid service token.
 */
fun Route.authenticatedService(build: Route.() -> Unit) = authenticate(AUTH_SERVICE) {
    build()
}

/**
 * Hybrid authenticated routes.
 * Ensures the request is authenticated with either a valid user or service token.
 */
fun Route.authenticated(build: Route.() -> Unit) = authenticate(AUTH_USER, AUTH_SERVICE) {
    build()
}

suspend fun RoutingContext.userPrincipal(): UserPrincipal =
    call.principal<UserPrincipal>() ?: throw AuthenticationException("User principal not found")

suspend fun RoutingContext.servicePrincipal(): ServicePrincipal =
    call.principal<ServicePrincipal>() ?: throw AuthenticationException("Service principal not found")

suspend fun RoutingContext.authPrincipal(): AuthPrincipal =
    call.principal<AuthPrincipal>() ?: throw AuthenticationException("Principal not found")

suspend fun RoutingContext.hasScope(scope: String): Boolean =
    call.principal<AuthPrincipal>()?.scopes?.contains(scope) == true

suspend fun RoutingContext.hasRole(role: String): Boolean =
    call.principal<UserPrincipal>()?.roles?.contains(role) == true

fun AuthPrincipal.requireScope(scope: String) {
    if (scope !in scopes) {
        throw AuthenticationException("Missing required scope: $scope")
    }
}

/**
 * Requires a specific scope to be present in the principal's scopes.
 */
fun Route.requireScope(scope: String, build: Route.() -> Unit) {
    val plugin = createRouteScopedPlugin("RequireScope-$scope") {
        onCall { call ->
            val principal = call.principal<AuthPrincipal>()
            if (principal == null || scope !in principal.scopes) {
                call.respond(HttpStatusCode.Forbidden, "Missing required scope: $scope")
            }
        }
    }
    install(plugin)
    build()
}

/**
 * Requires a specific role to be present in the UserPrincipal's roles.
 * Only works for UserPrincipal.
 */
fun Route.requireRole(role: String, build: Route.() -> Unit) {
    val plugin = createRouteScopedPlugin("RequireRole-$role") {
        onCall { call ->
            val principal = call.principal<UserPrincipal>()
            if (principal == null || role !in principal.roles) {
                call.respond(HttpStatusCode.Forbidden, "Missing required role: $role")
            }
        }
    }
    install(plugin)
    build()
}

class AuthenticationException(message: String) : RuntimeException(message)

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
    val config = client.discover(auth.issuer)

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
                        scopes = credential.payload.getClaim("scope")?.asString()?.split(" ")?.toSet() ?: emptySet()
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

fun extractRoles(realmAccessClaim: Claim?): Set<String> {
    val realmAccess = realmAccessClaim?.asMap()
    val roles = realmAccess?.get("roles")
    return if (roles is List<*>) {
        roles.filterIsInstance<String>().toSet()
    } else {
        emptySet()
    }
}

sealed interface AuthPrincipal {
    val scopes: Set<String>
}

data class ServicePrincipal(val serviceId: String, override val scopes: Set<String>) : AuthPrincipal

data class UserPrincipal(
    val userId: String,
    val email: String? = null,
    val name: String? = null,
    val roles: Set<String>,
    override val scopes: Set<String>,
) : AuthPrincipal

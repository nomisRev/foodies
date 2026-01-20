package io.ktor.foodies.server.openid

import com.auth0.jwt.interfaces.Claim
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
    @SerialName("user_audience") val userAudience: String
)

const val AUTH_USER = "auth-user"
private const val AUTH_SERVICE = "auth-service"

fun interface ServerSessionSCope {
    context(ctx: RoutingContext)
    suspend fun serviceCredentials(): ServicePrincipal
}

context(scope: ServerSessionSCope)
suspend fun RoutingContext.serviceCredentials(): ServicePrincipal = scope.serviceCredentials()

fun Route.withServiceScope(build: context(ServerSessionSCope) Route.() -> Unit): Route = authenticate(AUTH_SERVICE) {
    build.invoke(ServerSessionSCope { contextOf<RoutingContext>().call.principal<ServicePrincipal>()!! }, this)
}

fun interface UserSessionSope {
    context(ctx: RoutingContext)
    suspend fun userCredentials(): UserPrincipal
}

context(scope: UserSessionSope)
suspend fun RoutingContext.userCredentials(): UserPrincipal = scope.userCredentials()

fun Route.withUserScope(build: context(UserSessionSope) Route.() -> Unit): Route = authenticate(AUTH_USER) {
    build.invoke(UserSessionSope {
        requireNotNull(contextOf<RoutingContext>().call.principal<UserPrincipal>()) {
            "User principal not found"
        }
    }, this)
}

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
                        subject = sub,
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

fun extractRoles(realmAccessClaim: Claim?): Set<String> =
    try {
        @Suppress("UNCHECKED_CAST")
        val realmAccess = realmAccessClaim?.asMap()
        (realmAccess?.get("roles") as? List<String>)?.toSet() ?: emptySet()
    } catch (_: Exception) {
        emptySet()
    }

data class ServicePrincipal(val serviceId: String, val scopes: Set<String>)

data class UserPrincipal(
    val subject: String,
    val email: String? = null,
    val name: String? = null,
    val roles: Set<String>,
    val scopes: Set<String>,
)

package io.ktor.foodies.server.openid

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
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

val RoleAuthorization = createRouteScopedPlugin(
    name = "RoleAuthorization",
    createConfiguration = ::RoleAuthorizationConfiguration
) {
    val role = pluginConfig.role
    on(AuthenticationChecked) { call ->
        val principal = call.principal<JWTPrincipal>()
        val realmAccess = principal?.payload?.getClaim("realm_access")?.asMap()
        val roles = realmAccess?.get("roles") as? List<Any?> ?: emptyList<Any?>()
        if (!roles.any { it.toString() == role }) {
            call.respond(HttpStatusCode.Forbidden, "Role $role required")
        }
    }
}

class RoleAuthorizationConfiguration {
    var role: String = ""
}

fun Route.withRole(role: String, build: Route.() -> Unit): Route {
    val authorizedRoute = createChild(object : RouteSelector() {
        override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
            RouteSelectorEvaluation.Constant
    })
    authorizedRoute.install(RoleAuthorization) {
        this.role = role
    }
    authorizedRoute.build()
    return authorizedRoute
}

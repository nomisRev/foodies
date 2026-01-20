package io.ktor.foodies.server.openid

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext

val ServiceAuthorizationPlugin = createRouteScopedPlugin("ServiceAuthorization") {
    onCall { call ->
        val principal = call.principal<JWTPrincipal>() ?: return@onCall
        
        // In a real scenario, we might have two tokens: one for service, one for user.
        // For now, let's assume the principal is the service principal.
        // We can enhance this later to distinguish between them.
        
        when (val result = validateServiceRequest(principal)) {
            is AuthResult.Authenticated -> {
                call.attributes.put(ServiceAuthContextKey, result.context)
            }
            else -> {
                // We don't fail the request here, just don't set the context.
                // Authorization will be handled by requireScope or similar.
            }
        }
    }
}

class AuthorizedRouteSelector : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
        RouteSelectorEvaluation.Constant
}

fun Route.requireScope(scope: String, build: Route.() -> Unit): Route {
    val authorizedRoute = createChild(AuthorizedRouteSelector())
    authorizedRoute.install(ServiceAuthorizationPlugin)
    authorizedRoute.install(createRouteScopedPlugin("ScopeValidator-$scope") {
        onCall { call ->
            val context = call.attributes.getOrNull(ServiceAuthContextKey)
            val result = context?.requireScope(scope) ?: AuthorizationResult.Unauthorized(scope)
            
            when (result) {
                is AuthorizationResult.Authorized -> {
                    // Proceed
                }
                is AuthorizationResult.Unauthorized -> {
                    call.respond(HttpStatusCode.Unauthorized)
                }
                is AuthorizationResult.Forbidden -> {
                    call.respond(HttpStatusCode.Forbidden, result.reason)
                }
            }
        }
    })
    authorizedRoute.apply(build)
    return authorizedRoute
}

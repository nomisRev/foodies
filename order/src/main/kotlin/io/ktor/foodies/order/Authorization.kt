package io.ktor.foodies.order

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.*

val RoleAuthorization =
    createRouteScopedPlugin(
        name = "RoleAuthorization",
        createConfiguration = ::RoleAuthorizationConfiguration,
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
    val authorizedRoute =
        createChild(
            object : RouteSelector() {
                override suspend fun evaluate(
                    context: RoutingResolveContext,
                    segmentIndex: Int,
                ): RouteSelectorEvaluation = RouteSelectorEvaluation.Constant
            }
        )
    authorizedRoute.install(RoleAuthorization) { this.role = role }
    authorizedRoute.build()
    return authorizedRoute
}

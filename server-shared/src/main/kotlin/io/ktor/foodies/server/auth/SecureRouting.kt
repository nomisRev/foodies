package io.ktor.foodies.server.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.*

private class ServiceAuthContextConfiguration {
    var requiredRoles: Array<out String> = emptyArray()
}

private val ServiceAuthContextPlugin = createRouteScopedPlugin(
    name = "ServiceAuthContext",
    createConfiguration = ::ServiceAuthContextConfiguration
) {
    val requiredRoles = pluginConfig.requiredRoles

    on(AuthenticationChecked) { call ->
        val principal = call.principal<ServicePrincipal>()

        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized)
            return@on
        }

        if (requiredRoles.isNotEmpty()) {
            val missingRoles = requiredRoles.filter { it !in principal.roles }
            if (missingRoles.isNotEmpty()) {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}

fun Route.secureUser(build: Route.() -> Unit): Route {
    return authenticate("user", build = build)
}

fun Route.secureService(
    vararg requiredRoles: String,
    build: Route.() -> Unit
): Route {
    val authenticatedRoute = authenticate("service") {
        val authorizedRoute = createChild(object : RouteSelector() {
            override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
                RouteSelectorEvaluation.Constant
        })
        authorizedRoute.install(ServiceAuthContextPlugin) {
            this.requiredRoles = requiredRoles
        }
        authorizedRoute.build()
    }
    return authenticatedRoute
}

fun ApplicationCall.userPrincipal(): UserPrincipal =
    principal<UserPrincipal>()
        ?: error("UserPrincipal not found - route not properly secured with secureUser")

fun ApplicationCall.servicePrincipal(): ServicePrincipal =
    principal<ServicePrincipal>()
        ?: error("ServicePrincipal not found - route not properly secured with secureService")

package io.ktor.foodies.server.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.coroutines.withContext

fun Route.secureUser(build: Route.() -> Unit): Route {
    return authenticate("user") {
        intercept(ApplicationCallPipeline.Call) {
            val principal = call.principal<UserPrincipal>()
                ?: return@intercept call.respond(HttpStatusCode.Unauthorized)
            withContext(AuthContext.UserAuth(principal.accessToken)) {
                proceed()
            }
        }
        build()
    }
}

fun Route.secureService(
    vararg requiredRoles: String,
    build: Route.() -> Unit
): Route {
    return authenticate("service") {
        intercept(ApplicationCallPipeline.Call) {
            val principal = call.principal<ServicePrincipal>()
                ?: return@intercept call.respond(HttpStatusCode.Unauthorized)

            if (requiredRoles.isNotEmpty()) {
                val missingRoles = requiredRoles.filter { it !in principal.roles }
                if (missingRoles.isNotEmpty()) {
                    return@intercept call.respond(HttpStatusCode.Forbidden)
                }
            }

            val userContextToken = call.request.headers["X-User-Context"]?.removePrefix("Bearer ")
            val serviceToken = call.request.headers["Authorization"]!!.removePrefix("Bearer ")
            val authContext = AuthContext.ServiceAuth(serviceToken, userContextToken)

            withContext(authContext) {
                proceed()
            }
        }
        build()
    }
}

fun ApplicationCall.userPrincipal(): UserPrincipal =
    principal<UserPrincipal>()
        ?: error("UserPrincipal not found - route not properly secured with secureUser")

fun ApplicationCall.servicePrincipal(): ServicePrincipal =
    principal<ServicePrincipal>()
        ?: error("ServicePrincipal not found - route not properly secured with secureService")

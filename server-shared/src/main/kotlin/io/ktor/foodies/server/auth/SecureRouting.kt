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

fun interface SecuredUser {
    suspend fun userPrincipal(call: ApplicationCall): UserPrincipal
}

fun interface SecuredService {
    suspend fun servicePrincipal(call: ApplicationCall): ServicePrincipal
}

fun Route.secureUser(build: context(SecuredUser) Route.() -> Unit): Route {
    return authenticate("user") {
        intercept(ApplicationCallPipeline.Call) {
            val principal = call.principal<UserPrincipal>()
                ?: return@intercept call.respond(HttpStatusCode.Unauthorized)
            withContext(AuthContext.UserAuth(principal.accessToken)) {
                proceed()
            }
        }
        val securedUser = SecuredUser { applicationCall ->
            applicationCall.principal<UserPrincipal>()
                ?: error("UserPrincipal not found - route not properly secured with secureUser")
        }
        with(securedUser) {
            build()
        }
    }
}

fun Route.secureService(
    vararg requiredRoles: String,
    build: context(SecuredService) Route.() -> Unit
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
        val securedService = SecuredService { applicationCall ->
            applicationCall.principal<ServicePrincipal>()
                ?: error("ServicePrincipal not found - route not properly secured with secureService")
        }
        with(securedService) {
            build()
        }
    }
}

context(securedUser: SecuredUser)
suspend fun ApplicationCall.userPrincipal(): UserPrincipal = securedUser.userPrincipal(this)

context(securedService: SecuredService)
suspend fun ApplicationCall.servicePrincipal(): ServicePrincipal = securedService.servicePrincipal(this)

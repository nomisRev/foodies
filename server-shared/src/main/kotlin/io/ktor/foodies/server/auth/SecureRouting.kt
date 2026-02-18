package io.ktor.foodies.server.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.coroutines.withContext

fun interface SecuredUser {
    context(ctx: RoutingContext)
    suspend fun userPrincipal(): UserPrincipal
}

context(secured: SecuredUser, context: RoutingContext)
suspend fun userPrincipal(): UserPrincipal = secured.userPrincipal()


fun interface SecuredService {
    context(ctx: RoutingContext)
    suspend fun servicePrincipal(): ServicePrincipal
}

fun Route.secureUser(
    vararg roles: String,
    build: context(SecuredUser) Route.() -> Unit
): Route = authenticate("user") {
    install(createRouteScopedPlugin("SecureUserContext") {
        route!!.intercept(ApplicationCallPipeline.Call) {
            val principal = requireNotNull(call.principal<UserPrincipal>()) { "UserPrincipal not found" }
            if (roles.isNotEmpty()) {
                val missingRoles = roles.filter { it !in principal.roles }
                if (missingRoles.isNotEmpty()) {
                    return@intercept call.respond(HttpStatusCode.Forbidden)
                }
            }
            withContext(AuthContext(principal.accessToken)) { proceed() }
        }
    })
    with(SecuredUser {
        requireNotNull(contextOf<RoutingContext>().call.principal<UserPrincipal>()) {
            "UserPrincipal not found - route not properly secured with secureUser"
        }
    }) {
        build()
    }
}

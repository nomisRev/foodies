package io.ktor.foodies.server.security

import io.ktor.foodies.server.auth.AuthContext
import io.ktor.foodies.server.auth.SecuredUser
import io.ktor.foodies.server.auth.UserPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import kotlinx.coroutines.withContext

fun interface UserSessionScope {
    context(ctx: RoutingContext)
    suspend fun userSession(): UserSession
}

context(scope: UserSessionScope)
suspend fun RoutingContext.userSession(): UserSession = scope.userSession()

fun Route.public(build: Route.() -> Unit): Route = authenticate(optional = true) {
    install(createRouteScopedPlugin("SecureUserSession") {
        route!!.intercept(ApplicationCallPipeline.Call) {
            val user = call.principal<UserSession>()
            if (user == null) proceed()
            else withContext(AuthContext.UserAuth(user.accessToken)) { proceed() }
        }
    })
    build()
}

fun Route.userSession(build: context(UserSessionScope) Route.() -> Unit): Route = authenticate {
    install(createRouteScopedPlugin("SecureUserSession") {
        route!!.intercept(ApplicationCallPipeline.Call) {
            val principal = requireNotNull(call.principal<UserSession>()) { "UserSession not found" }
            withContext(AuthContext.UserAuth(principal.idToken)) { proceed() }
        }
    })
    with(UserSessionScope {
        requireNotNull(contextOf<RoutingContext>().call.principal<UserSession>()) {
            "UserSession not found - route not properly secured with userSession"
        }
    }) {
        build()
    }
}

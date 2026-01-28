package io.ktor.foodies.server.security

import io.ktor.foodies.server.auth.AuthContext
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import kotlinx.coroutines.withContext

fun interface UserSessionScope {
    context(ctx: RoutingContext)
    suspend fun userSession(): UserSession
}

context(scope: UserSessionScope)
suspend fun RoutingContext.userSession(): UserSession = scope.userSession()

fun Route.withUserSession(build: context(UserSessionScope) Route.() -> Unit): Route = authenticate {
    install(createRouteScopedPlugin("UserAuthContext") {
        route!!.intercept(ApplicationCallPipeline.Call) {
            val session = call.principal<UserSession>()
            if (session != null) {
                withContext(AuthContext.UserAuth(session.accessToken)) {
                    proceed()
                }
            } else {
                proceed()
            }
        }
    })
    build.invoke(UserSessionScope { contextOf<RoutingContext>().call.principal<UserSession>()!! }, this)
}

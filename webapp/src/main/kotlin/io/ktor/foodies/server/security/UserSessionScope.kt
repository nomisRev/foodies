package io.ktor.foodies.server.security

import io.ktor.foodies.server.openid.withAuthContext
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.routing.*
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.coroutines.withContext

fun interface UserSessionScope {
    context(ctx: RoutingContext)
    suspend fun userSession(): UserSession
}

context(scope: UserSessionScope)
suspend fun RoutingContext.userSession(): UserSession = scope.userSession()

fun Route.withUserSession(build: context(UserSessionScope) Route.() -> Unit): Route = authenticate {
    this.intercept(ApplicationCallPipeline.Plugins) {
        val session = call.sessions.get<UserSession>()
        if (session != null) {
            call.withAuthContext<Unit>(session.accessToken) {
                proceed()
            }
        } else {
            proceed()
        }
    }
    build.invoke(UserSessionScope { contextOf<RoutingContext>().call.principal<UserSession>()!! }, this)
}

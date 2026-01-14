package io.ktor.foodies.server.security

import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext

fun interface UserSessionScope {
    context(ctx: RoutingContext)
    suspend fun userSession(): UserSession
}

context(scope: UserSessionScope)
suspend fun RoutingContext.userSession(): UserSession = scope.userSession()

fun Route.withUserSession(build: context(UserSessionScope) Route.() -> Unit): Route = authenticate {
    build.invoke(UserSessionScope { contextOf<RoutingContext>().call.principal<UserSession>()!! }, this)
}

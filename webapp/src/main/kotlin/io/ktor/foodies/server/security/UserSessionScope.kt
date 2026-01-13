package io.ktor.foodies.server.security

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

fun interface UserSessionScope {
    suspend fun RoutingContext.session(): UserSession
}

inline fun Route.withUserSession(build: UserSessionScope.() -> Unit): Route = apply {
    install(createRouteScopedPlugin("SessionAuth") {
        on(AuthenticationChecked) { call ->
            val session = call.sessions.get<UserSession>()
            if (session == null) {
                call.response.headers.append("HX-Redirect", "/login")
                call.respond(HttpStatusCode.Unauthorized)
            }
        }
    })

    build(UserSessionScope { call.sessions.get<UserSession>()!! })
}

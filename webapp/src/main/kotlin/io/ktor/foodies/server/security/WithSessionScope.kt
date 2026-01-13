package io.ktor.foodies.server.security

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

fun interface WithSessionScope {
    suspend fun RoutingContext.session(): UserSession
}

inline fun Route.withSession(build: WithSessionScope.() -> Unit): Route = apply {
    install(createRouteScopedPlugin("SessionAuth") {
        onCall { call ->
            val session = call.sessions.get<UserSession>()
            if (session == null) {
                call.response.headers.append("HX-Redirect", "/login")
                call.respond(HttpStatusCode.Unauthorized)
            }
        }
    })

    build(WithSessionScope { call.sessions.get<UserSession>()!! })
}
package io.ktor.foodies.webapp.security

import io.ktor.foodies.server.auth.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.oidc.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.withContext

@OptIn(ExperimentalKtorApi::class)
fun Route.authenticatedSession(
    provider: OidcProvider<OidcToken.Id>,
    onUnauthorized: UnauthorizedHandler = { _ ->
        call.response.headers.append("HX-Redirect", "/login")
        call.respond(HttpStatusCode.Unauthorized)
    },
    build: Route.() -> Unit,
): Route = authenticateWith(provider.sessions, onUnauthorized = onUnauthorized) {
    val ctx = authenticatedContext()

    install(createRouteScopedPlugin("AuthenticatedSessionContext") {
        route!!.intercept(ApplicationCallPipeline.Call) {
            val token = ctx.principal(call).accessToken
            withContext(AuthContext(token)) { proceed() }
        }
    })

    build()
}

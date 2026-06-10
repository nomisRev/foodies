package io.ktor.foodies.server.auth

import io.ktor.foodies.server.openid.*
import io.ktor.server.application.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.withContext

@OptIn(ExperimentalKtorApi::class)
fun Route.secure(
    vararg roles: UserRole,
    build: context(RoleBasedContext<UserPrincipal, UserRole>) Route.() -> Unit
): Route = authenticateWith(application.authScheme, roles = roles.toSet()) {
    val ctx = authenticatedContext()

    install(createRouteScopedPlugin("SecureUserContext") {
        route!!.intercept(ApplicationCallPipeline.Call) {
            val user = ctx.principal(call)
            val accessToken = user.accessToken.value
            withContext(AuthContext(accessToken)) {
                proceed()
            }
        }
    })

    build()
}

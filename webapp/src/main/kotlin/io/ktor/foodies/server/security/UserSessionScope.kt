package io.ktor.foodies.server.security

import io.ktor.server.routing.RoutingContext
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class UserSession(
    val idToken: String,
    val accessToken: String,
    val expiresIn: Long,
    val refreshToken: String,
    val expiresAt: Instant,
)

fun interface UserSessionScope {
    context(ctx: RoutingContext)
    suspend fun userSession(): UserSession
}

context(scope: UserSessionScope)
suspend fun RoutingContext.userSession(): UserSession = scope.userSession()

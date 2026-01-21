package io.ktor.foodies.server.auth

import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds

data class ServiceToken(
    val accessToken: String,
    val expiresAt: Instant,
    val tokenType: String = "Bearer"
) {
    fun isExpired(bufferSeconds: Long = 30): Boolean =
        Clock.System.now() >= expiresAt - bufferSeconds.seconds
}

interface ServiceTokenProvider {
    suspend fun getToken(): ServiceToken

    suspend fun <T> withServiceAuth(
        userToken: String? = null,
        block: suspend () -> T
    ): T {
        val token = getToken()
        return withContext(AuthContext.ServiceAuth(token.accessToken, userToken)) {
            block()
        }
    }
}

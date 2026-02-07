package io.ktor.foodies.server.auth

import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

data class ServiceToken(
    val accessToken: String,
    val expiresAt: Instant,
    val tokenType: String = "Bearer",
) {
    fun isExpired(bufferSeconds: Long = 30): Boolean =
        Clock.System.now() >= expiresAt - bufferSeconds.seconds
}

fun interface ServiceTokenProvider {
    suspend fun getToken(): ServiceToken
}

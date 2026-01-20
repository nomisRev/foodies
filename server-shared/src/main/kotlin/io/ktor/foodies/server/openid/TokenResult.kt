package io.ktor.foodies.server.openid

import java.time.Instant

sealed interface TokenResult {
    data class Success(val token: String, val expiresAt: Instant) : TokenResult
    data class Failed(val message: String, val cause: Throwable? = null) : TokenResult
}

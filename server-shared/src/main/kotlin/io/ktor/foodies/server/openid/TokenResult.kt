package io.ktor.foodies.server.openid

import kotlinx.datetime.Instant

/**
 * Represents the result of a token acquisition operation.
 * Uses sealed interface for type-safe handling without exceptions for control flow.
 */
sealed interface TokenResult {
    /**
     * Token acquired successfully.
     *
     * @property accessToken The JWT access token
     * @property expiresAt When the token expires
     * @property scopes The scopes granted for this token
     */
    data class Success(
        val accessToken: String,
        val expiresAt: Instant,
        val scopes: Set<String>
    ) : TokenResult

    /**
     * Token acquisition failed.
     *
     * @property reason Human-readable error message
     * @property errorCode OAuth2 error code (e.g., "invalid_client", "unauthorized_client")
     */
    data class Failed(
        val reason: String,
        val errorCode: String? = null
    ) : TokenResult
}

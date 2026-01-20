package io.ktor.foodies.server.openid

/**
 * Represents the result of an authentication operation.
 * Uses sealed interface for type-safe handling without exceptions for control flow.
 */
sealed interface AuthResult<out T> {
    /**
     * Authentication succeeded with the authenticated context.
     */
    data class Authenticated<T>(val value: T) : AuthResult<T>

    /**
     * Authentication failed - credentials missing or not provided.
     */
    data object Unauthenticated : AuthResult<Nothing>

    /**
     * Authentication failed - credentials provided but invalid (expired, malformed, tampered).
     */
    data class Invalid(val reason: String) : AuthResult<Nothing>
}

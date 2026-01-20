package io.ktor.foodies.server.openid

/**
 * Represents the result of an authorization check.
 * Uses sealed interface for type-safe handling without exceptions for control flow.
 */
sealed interface AuthorizationResult {
    /**
     * Authorization succeeded - the principal has the required permissions.
     */
    data object Authorized : AuthorizationResult

    /**
     * Authorization failed - the principal lacks required permissions.
     *
     * @property reason Human-readable explanation of why authorization failed
     *                  (e.g., "Missing required scope: basket:items:write",
     *                        "User does not own this resource")
     */
    data class Unauthorized(val reason: String) : AuthorizationResult

    /**
     * Authorization cannot be determined - authentication is required first.
     */
    data object Unauthenticated : AuthorizationResult
}

package io.ktor.foodies.server.openid

import com.auth0.jwt.interfaces.DecodedJWT

/**
 * Represents the result of JWT verification and validation.
 * Uses sealed interface for type-safe handling without exceptions for control flow.
 */
sealed interface JwtVerifyResult {
    /**
     * JWT is valid and verified successfully.
     *
     * @property jwt The decoded and verified JWT
     */
    data class Valid(val jwt: DecodedJWT) : JwtVerifyResult

    /**
     * JWT verification failed.
     *
     * @property reason Human-readable error message describing why verification failed
     *                  (e.g., "Token expired", "Invalid signature", "Invalid issuer")
     */
    data class Invalid(val reason: String) : JwtVerifyResult

    /**
     * JWT is missing or not provided.
     */
    data object Missing : JwtVerifyResult
}

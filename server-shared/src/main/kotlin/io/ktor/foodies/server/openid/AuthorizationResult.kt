package io.ktor.foodies.server.openid

sealed interface AuthorizationResult {
    data object Authorized : AuthorizationResult
    data class Unauthorized(val missingScope: String) : AuthorizationResult
    data class Forbidden(val reason: String) : AuthorizationResult
}

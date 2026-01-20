package io.ktor.foodies.server.openid

sealed interface AuthResult<out T> {
    data class Authenticated<T>(val context: T) : AuthResult<T>
    data object Unauthenticated : AuthResult<Nothing>
    data class Invalid(val reason: String) : AuthResult<Nothing>
}

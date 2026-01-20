package io.ktor.foodies.server.openid

sealed interface JwtVerifyResult<out T> {
    data class Valid<T>(val payload: T) : JwtVerifyResult<T>
    data object Invalid : JwtVerifyResult<Nothing>
    data object Expired : JwtVerifyResult<Nothing>
}

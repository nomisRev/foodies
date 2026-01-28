package io.ktor.foodies.server.auth

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

sealed class AuthContext : AbstractCoroutineContextElement(Key) {
    override val key: CoroutineContext.Key<*> get() = Key

    data class UserAuth(val accessToken: String) : AuthContext()

    data class ServiceAuth(val serviceToken: String, val userToken: String? = null) : AuthContext()

    companion object Key : CoroutineContext.Key<AuthContext>
}

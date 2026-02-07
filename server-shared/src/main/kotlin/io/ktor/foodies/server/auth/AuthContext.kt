package io.ktor.foodies.server.auth

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

sealed interface AuthContext : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = Key

    data class UserAuth(val accessToken: String) :
        AuthContext, AbstractCoroutineContextElement(Key) {
        override val key: CoroutineContext.Key<*>
            get() = AuthContext.Key
    }

    data class ServiceAuth(val serviceToken: String, val userToken: String? = null) :
        AuthContext, AbstractCoroutineContextElement(Key) {
        override val key: CoroutineContext.Key<*>
            get() = AuthContext.Key
    }

    companion object Key : CoroutineContext.Key<AuthContext>
}

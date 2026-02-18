package io.ktor.foodies.server.auth

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

data class AuthContext(val accessToken: String) : AbstractCoroutineContextElement(Key) {
    override val key: CoroutineContext.Key<*> get() = Key

    companion object Key : CoroutineContext.Key<AuthContext>
}

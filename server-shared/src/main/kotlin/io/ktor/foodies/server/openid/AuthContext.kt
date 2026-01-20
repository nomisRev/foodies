package io.ktor.foodies.server.openid

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

sealed interface AuthContext : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key

    data class UserAuth(val accessToken: String) : AuthContext, AbstractCoroutineContextElement(Key) {
        override val key: CoroutineContext.Key<*> get() = AuthContext.Key
    }

    data class ServiceAuth(
        val serviceToken: String,
        val userToken: String? = null
    ) : AuthContext, AbstractCoroutineContextElement(Key) {
        override val key: CoroutineContext.Key<*> get() = AuthContext.Key
    }

    companion object Key : CoroutineContext.Key<AuthContext>
}

val AuthContextPlugin = createClientPlugin("AuthContext") {
    onRequest { request, _ ->
        when (val auth = currentCoroutineContext()[AuthContext]) {
            is AuthContext.UserAuth -> {
                request.header(HttpHeaders.Authorization, "Bearer ${auth.accessToken}")
            }
            is AuthContext.ServiceAuth -> {
                request.header(HttpHeaders.Authorization, "Bearer ${auth.serviceToken}")
                auth.userToken?.let {
                    request.header("X-User-Context", "Bearer $it")
                }
            }
            null -> {}
        }
    }
}

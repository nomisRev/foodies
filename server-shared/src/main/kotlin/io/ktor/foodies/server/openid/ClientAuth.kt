package io.ktor.foodies.server.openid

import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import kotlinx.coroutines.currentCoroutineContext
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine context element to propagate authentication tokens.
 * Similar to MDC for logging, but for authentication.
 */
data class AuthContext(
    val userToken: String? = null,
    val serviceToken: String? = null
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<AuthContext>
    override val key: CoroutineContext.Key<*> get() = Key
}

/**
 * Creates an HttpClient that automatically propagates user tokens.
 * Extracts the Authorization header from the current request context
 * and forwards it to downstream service calls.
 *
 * Use for service-to-service calls that should maintain user identity.
 *
 * @return HttpClient configured with user token propagation
 */
fun HttpClient.withUserAuth(): HttpClient {
    return config {
        install(createClientPlugin("UserAuthPlugin") {
            onRequest { request, _ ->
                // Try to get token from coroutine context
                val authContext = currentCoroutineContext()[AuthContext]
                val token = authContext?.userToken

                if (token != null) {
                    request.header(HttpHeaders.Authorization, "Bearer $token")
                }
            }
        })
    }
}

/**
 * Smart client that automatically selects user or service auth based on context.
 * Checks coroutine context for user token first, falls back to service token.
 *
 * Use when you want automatic selection based on calling context.
 *
 * @param tokenClient Client for service tokens (fallback)
 * @return HttpClient with automatic auth selection
 */
fun HttpClient.withAuth(tokenClient: ServiceTokenClient): HttpClient {
    return config {
        install(createClientPlugin("AutoAuthPlugin") {
            onRequest { request, _ ->
                val authContext = currentCoroutineContext()[AuthContext]
                val token = authContext?.userToken ?: tokenClient.getAccessToken()
                request.header(HttpHeaders.Authorization, "Bearer $token")
            }
        })
    }
}

/**
 * Extension to extract and propagate authentication from ApplicationCall.
 * Use in route handlers before calling downstream services.
 */
suspend fun <R> ApplicationCall.withAuthContext(token: String? = null, block: suspend () -> R): R {
    // Try to get token from parameter, then from Authorization header
    val finalToken = token ?: request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")

    return kotlinx.coroutines.withContext(AuthContext(userToken = finalToken)) {
        block()
    }
}

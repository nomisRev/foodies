package io.ktor.foodies.server.auth

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.currentCoroutineContext

val AuthContextPlugin = createClientPlugin("AuthContext") {
    onRequest { request, _ ->
        when (val auth = currentCoroutineContext()[AuthContext]) {
            is AuthContext.UserAuth -> {
                request.headers[HttpHeaders.Authorization] = "Bearer ${auth.accessToken}"
            }
            is AuthContext.ServiceAuth -> {
                request.headers[HttpHeaders.Authorization] = "Bearer ${auth.serviceToken}"
                auth.userToken?.let { request.headers["X-User-Context"] = "Bearer $it" }
            }
            null -> {}
        }
    }
}

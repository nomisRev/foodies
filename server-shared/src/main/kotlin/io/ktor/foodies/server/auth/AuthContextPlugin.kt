package io.ktor.foodies.server.auth

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.currentCoroutineContext
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("AuthContextPlugin")

val AuthContextPlugin = createClientPlugin("AuthContext") {
    onRequest { request, _ ->
        when (val auth = currentCoroutineContext()[AuthContext]) {
            is AuthContext -> {
                request.headers[HttpHeaders.Authorization] = "Bearer ${auth.accessToken}"
                logger.info("Injecting user token into request")
            }

            null -> logger.info("No auth context found")
        }
    }
}

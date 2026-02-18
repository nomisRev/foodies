package io.ktor.foodies.server.auth

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.withContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun createMockClient(capturedHeaders: MutableMap<String, String>): HttpClient {
    return HttpClient(MockEngine) {
        install(AuthContextPlugin)
        engine {
            addHandler { request ->
                request.headers.forEach { name, values ->
                    capturedHeaders[name] = values.first()
                }
                respond("OK", HttpStatusCode.OK)
            }
        }
    }
}

private fun createMultiRequestMockClient(requests: MutableList<Map<String, String>>): HttpClient {
    return HttpClient(MockEngine) {
        install(AuthContextPlugin)
        engine {
            addHandler { request ->
                val headers = mutableMapOf<String, String>()
                request.headers.forEach { name, values ->
                    headers[name] = values.first()
                }
                requests.add(headers)
                respond("OK", HttpStatusCode.OK)
            }
        }
    }
}

val authContextPluginSpec by testSuite {
    test("should inject user token from coroutine context") {
        val capturedHeaders = mutableMapOf<String, String>()
        val client = createMockClient(capturedHeaders)

        withContext(AuthContext("user-jwt-token")) {
            client.get("http://example.com/api")
        }

        assertEquals("Bearer user-jwt-token", capturedHeaders[HttpHeaders.Authorization])
        assertFalse(capturedHeaders.containsKey("X-User-Context"))
    }

    test("should not inject headers when no auth context present") {
        val capturedHeaders = mutableMapOf<String, String>()
        val client = createMockClient(capturedHeaders)

        client.get("http://example.com/api")

        assertFalse(capturedHeaders.containsKey(HttpHeaders.Authorization))
        assertFalse(capturedHeaders.containsKey("X-User-Context"))
    }

    test("should correctly format Bearer token in Authorization header") {
        var authHeader: String? = null
        val client = HttpClient(MockEngine) {
            install(AuthContextPlugin)
            engine {
                addHandler { request ->
                    authHeader = request.headers[HttpHeaders.Authorization]
                    respond("OK", HttpStatusCode.OK)
                }
            }
        }

        withContext(AuthContext("my-token-value")) {
            client.get("http://example.com/api")
        }

        assertEquals("Bearer my-token-value", authHeader)
        assertTrue(authHeader!!.startsWith("Bearer "))
    }
}

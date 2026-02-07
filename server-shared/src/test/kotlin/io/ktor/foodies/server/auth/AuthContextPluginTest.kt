package io.ktor.foodies.server.auth

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.withContext

private fun createMockClient(capturedHeaders: MutableMap<String, String>): HttpClient {
    return HttpClient(MockEngine) {
        install(AuthContextPlugin)
        engine {
            addHandler { request ->
                request.headers.forEach { name, values -> capturedHeaders[name] = values.first() }
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
                request.headers.forEach { name, values -> headers[name] = values.first() }
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

        withContext(AuthContext.UserAuth("user-jwt-token")) { client.get("http://example.com/api") }

        assertEquals("Bearer user-jwt-token", capturedHeaders[HttpHeaders.Authorization])
        assertFalse(capturedHeaders.containsKey("X-User-Context"))
    }

    test("should inject service token without user context") {
        val capturedHeaders = mutableMapOf<String, String>()
        val client = createMockClient(capturedHeaders)

        withContext(AuthContext.ServiceAuth("service-jwt-token")) {
            client.get("http://example.com/api")
        }

        assertEquals("Bearer service-jwt-token", capturedHeaders[HttpHeaders.Authorization])
        assertFalse(capturedHeaders.containsKey("X-User-Context"))
    }

    test("should inject service token with user context") {
        val capturedHeaders = mutableMapOf<String, String>()
        val client = createMockClient(capturedHeaders)

        withContext(AuthContext.ServiceAuth("service-jwt", "original-user-jwt")) {
            client.get("http://example.com/api")
        }

        assertEquals("Bearer service-jwt", capturedHeaders[HttpHeaders.Authorization])
        assertEquals("Bearer original-user-jwt", capturedHeaders["X-User-Context"])
    }

    test("should not inject headers when no auth context present") {
        val capturedHeaders = mutableMapOf<String, String>()
        val client = createMockClient(capturedHeaders)

        client.get("http://example.com/api")

        assertFalse(capturedHeaders.containsKey(HttpHeaders.Authorization))
        assertFalse(capturedHeaders.containsKey("X-User-Context"))
    }

    test("should handle multiple sequential requests with different contexts") {
        val requests = mutableListOf<Map<String, String>>()
        val client = createMultiRequestMockClient(requests)

        withContext(AuthContext.UserAuth("user-token-1")) { client.get("http://example.com/api/1") }

        withContext(AuthContext.ServiceAuth("service-token-2")) {
            client.get("http://example.com/api/2")
        }

        withContext(AuthContext.ServiceAuth("service-token-3", "user-token-3")) {
            client.get("http://example.com/api/3")
        }

        assertEquals(3, requests.size)
        assertEquals("Bearer user-token-1", requests[0][HttpHeaders.Authorization])
        assertNull(requests[0]["X-User-Context"])

        assertEquals("Bearer service-token-2", requests[1][HttpHeaders.Authorization])
        assertNull(requests[1]["X-User-Context"])

        assertEquals("Bearer service-token-3", requests[2][HttpHeaders.Authorization])
        assertEquals("Bearer user-token-3", requests[2]["X-User-Context"])
    }

    test("should handle nested context switches correctly") {
        val requests = mutableListOf<Map<String, String>>()
        val client = createMultiRequestMockClient(requests)

        withContext(AuthContext.UserAuth("outer-user-token")) {
            client.get("http://example.com/api/outer")

            withContext(AuthContext.ServiceAuth("inner-service-token")) {
                client.get("http://example.com/api/inner")
            }

            client.get("http://example.com/api/restored")
        }

        assertEquals(3, requests.size)
        assertEquals("Bearer outer-user-token", requests[0][HttpHeaders.Authorization])
        assertEquals("Bearer inner-service-token", requests[1][HttpHeaders.Authorization])
        assertEquals("Bearer outer-user-token", requests[2][HttpHeaders.Authorization])
    }

    test("should correctly format Bearer token in Authorization header") {
        var authHeader: String? = null
        val client =
            HttpClient(MockEngine) {
                install(AuthContextPlugin)
                engine {
                    addHandler { request ->
                        authHeader = request.headers[HttpHeaders.Authorization]
                        respond("OK", HttpStatusCode.OK)
                    }
                }
            }

        withContext(AuthContext.UserAuth("my-token-value")) { client.get("http://example.com/api") }

        assertEquals("Bearer my-token-value", authHeader)
        assertTrue(authHeader!!.startsWith("Bearer "))
    }

    test("should correctly format Bearer token in X-User-Context header") {
        var userContextHeader: String? = null
        val client =
            HttpClient(MockEngine) {
                install(AuthContextPlugin)
                engine {
                    addHandler { request ->
                        userContextHeader = request.headers["X-User-Context"]
                        respond("OK", HttpStatusCode.OK)
                    }
                }
            }

        withContext(AuthContext.ServiceAuth("service-token", "user-context-token")) {
            client.get("http://example.com/api")
        }

        assertEquals("Bearer user-context-token", userContextHeader)
        assertTrue(userContextHeader!!.startsWith("Bearer "))
    }
}

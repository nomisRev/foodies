package io.ktor.foodies.server.openid

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.withContext
import kotlin.test.assertEquals
import kotlin.test.assertNull

val clientAuthSpec by testSuite("ClientAuth") {
    test("should propagate token from AuthContext to header") {
        val token = "test-token"
        val engine = MockEngine { request ->
            val authHeader = request.headers[HttpHeaders.Authorization]
            assertEquals("Bearer $token", authHeader)
            respondOk()
        }
        val client = HttpClient(engine).withUserAuth()

        withContext(AuthContext(userToken = token)) {
            client.get("http://localhost/test")
        }
    }

    test("should not add Authorization header if token is missing in AuthContext") {
        val engine = MockEngine { request ->
            assertNull(request.headers[HttpHeaders.Authorization])
            respondOk()
        }
        val client = HttpClient(engine).withUserAuth()

        client.get("http://localhost/test")
    }

    test("should not add Authorization header if AuthContext is missing") {
        val engine = MockEngine { request ->
            assertNull(request.headers[HttpHeaders.Authorization])
            respondOk()
        }
        val client = HttpClient(engine).withUserAuth()

        client.get("http://localhost/test")
    }

    test("withAuth should use user token from AuthContext if present") {
        val userToken = "user-token"
        val engine = MockEngine { request ->
            assertEquals("Bearer $userToken", request.headers[HttpHeaders.Authorization])
            respondOk()
        }
        val serviceTokenClient = object : ServiceTokenClient {
            override suspend fun getAccessToken(): String = "service-token"
        }
        val client = HttpClient(engine).withAuth(serviceTokenClient)

        withContext(AuthContext(userToken = userToken)) {
            client.get("http://localhost/test")
        }
    }

    test("withAuth should use service token if user token is missing") {
        val serviceToken = "service-token"
        val engine = MockEngine { request ->
            assertEquals("Bearer $serviceToken", request.headers[HttpHeaders.Authorization])
            respondOk()
        }
        val serviceTokenClient = object : ServiceTokenClient {
            override suspend fun getAccessToken(): String = serviceToken
        }
        val client = HttpClient(engine).withAuth(serviceTokenClient)

        client.get("http://localhost/test")
    }
}

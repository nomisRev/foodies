package io.ktor.foodies.server.auth

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

val clientCredentialsTokenProviderSpec by testSuite {
    test("should cache token until expiration") {
        var requestCount = 0
        val mockEngine = MockEngine { request ->
            requestCount++
            val tokenResponse = TokenResponse(
                accessToken = "cached-token-$requestCount",
                expiresIn = 300,
                tokenType = "Bearer"
            )
            respond(
                content = Json.encodeToString(tokenResponse),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }

        val provider = ClientCredentialsTokenProvider(
            httpClient = client,
            tokenEndpoint = "http://keycloak/token",
            clientId = "test-service",
            clientSecret = "secret"
        )

        val token1 = provider.getToken()
        val token2 = provider.getToken()
        val token3 = provider.getToken()

        assertEquals(token1.accessToken, token2.accessToken)
        assertEquals(token1.accessToken, token3.accessToken)
        assertEquals(1, requestCount)
    }

    test("should refresh token when expired") {
        var requestCount = 0
        val mockEngine = MockEngine { request ->
            requestCount++
            val tokenResponse = TokenResponse(
                accessToken = "token-$requestCount",
                expiresIn = 1,
                tokenType = "Bearer"
            )
            respond(
                content = Json.encodeToString(tokenResponse),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }

        val provider = ClientCredentialsTokenProvider(
            httpClient = client,
            tokenEndpoint = "http://keycloak/token",
            clientId = "test-service",
            clientSecret = "secret"
        )

        val token1 = provider.getToken()
        delay(2.seconds)
        val token2 = provider.getToken()

        assertNotEquals(token1.accessToken, token2.accessToken)
        assertEquals("token-1", token1.accessToken)
        assertEquals("token-2", token2.accessToken)
        assertEquals(2, requestCount)
    }

    test("should handle concurrent token requests with mutex") {
        var requestCount = 0
        val mockEngine = MockEngine { request ->
            requestCount++
            delay(100)
            val tokenResponse = TokenResponse(
                accessToken = "concurrent-token-$requestCount",
                expiresIn = 300,
                tokenType = "Bearer"
            )
            respond(
                content = Json.encodeToString(tokenResponse),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }

        val provider = ClientCredentialsTokenProvider(
            httpClient = client,
            tokenEndpoint = "http://keycloak/token",
            clientId = "test-service",
            clientSecret = "secret"
        )

        val deferred1 = async { provider.getToken() }
        val deferred2 = async { provider.getToken() }
        val deferred3 = async { provider.getToken() }

        val token1 = deferred1.await()
        val token2 = deferred2.await()
        val token3 = deferred3.await()

        assertEquals(token1.accessToken, token2.accessToken)
        assertEquals(token1.accessToken, token3.accessToken)
        assertEquals(1, requestCount)
    }

    test("should send correct OAuth2 client credentials parameters") {
        var requestUrl: String? = null
        var requestMethod: String? = null
        var formData: Parameters? = null
        val mockEngine = MockEngine { request ->
            requestUrl = request.url.toString()
            requestMethod = request.method.value
            formData = (request.body as FormDataContent).formData
            val tokenResponse = TokenResponse(
                accessToken = "test-token",
                expiresIn = 300,
                tokenType = "Bearer"
            )
            respond(
                content = Json.encodeToString(tokenResponse),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }

        val provider = ClientCredentialsTokenProvider(
            httpClient = client,
            tokenEndpoint = "http://keycloak/token",
            clientId = "my-service",
            clientSecret = "my-secret",
            scope = "aud-order-service"
        )

        provider.getToken()

        assertEquals("http://keycloak/token", requestUrl)
        assertEquals("POST", requestMethod)
        assertEquals("client_credentials", formData?.get("grant_type"))
        assertEquals("my-service", formData?.get("client_id"))
        assertEquals("my-secret", formData?.get("client_secret"))
        assertEquals("aud-order-service", formData?.get("scope"))
    }

    test("should omit scope when not provided") {
        var formData: Parameters? = null
        val mockEngine = MockEngine { request ->
            formData = (request.body as FormDataContent).formData
            val tokenResponse = TokenResponse(
                accessToken = "test-token",
                expiresIn = 300,
                tokenType = "Bearer"
            )
            respond(
                content = Json.encodeToString(tokenResponse),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }

        val provider = ClientCredentialsTokenProvider(
            httpClient = client,
            tokenEndpoint = "http://keycloak/token",
            clientId = "my-service",
            clientSecret = "my-secret"
        )

        provider.getToken()

        assertEquals(null, formData?.get("scope"))
    }

    test("should calculate expiry time correctly") {
        val mockEngine = MockEngine { request ->
            val tokenResponse = TokenResponse(
                accessToken = "expiry-test-token",
                expiresIn = 300,
                tokenType = "Bearer"
            )
            respond(
                content = Json.encodeToString(tokenResponse),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }

        val provider = ClientCredentialsTokenProvider(
            httpClient = client,
            tokenEndpoint = "http://keycloak/token",
            clientId = "test-service",
            clientSecret = "secret"
        )

        val beforeRequest = Clock.System.now()
        val token = provider.getToken()
        val afterRequest = Clock.System.now()

        assertTrue(token.expiresAt >= beforeRequest + 300.seconds)
        assertTrue(token.expiresAt <= afterRequest + 300.seconds)
    }

    test("should propagate HTTP errors from token endpoint") {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"error": "invalid_client"}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }

        val provider = ClientCredentialsTokenProvider(
            httpClient = client,
            tokenEndpoint = "http://keycloak/token",
            clientId = "test-service",
            clientSecret = "wrong-secret"
        )

        assertFailsWith<Exception> {
            provider.getToken()
        }
    }

    test("should use Bearer as token type from response") {
        val mockEngine = MockEngine { request ->
            val tokenResponse = TokenResponse(
                accessToken = "type-test-token",
                expiresIn = 300,
                tokenType = "Bearer"
            )
            respond(
                content = Json.encodeToString(tokenResponse),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }

        val provider = ClientCredentialsTokenProvider(
            httpClient = client,
            tokenEndpoint = "http://keycloak/token",
            clientId = "test-service",
            clientSecret = "secret"
        )

        val token = provider.getToken()
        assertEquals("Bearer", token.tokenType)
    }
}

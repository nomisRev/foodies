package io.ktor.foodies.server.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class CachingServiceTokenClientTest {

    @Test
    fun `should cache token`() = runBlocking {
        var callCount = 0
        val issuer = "http://localhost:8080"
        val mockEngine = MockEngine { request ->
            when (request.url.toString()) {
                "$issuer/.well-known/openid-configuration" -> {
                    respond(
                        content = """{"issuer": "$issuer", "authorization_endpoint": "$issuer/auth", "token_endpoint": "$issuer/token", "jwks_uri": "$issuer/jwks", "end_session_endpoint": "$issuer/logout"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                "$issuer/token" -> {
                    callCount++
                    respond(
                        content = """{"access_token": "token-$callCount", "expires_in": 300, "token_type": "Bearer"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                else -> error("Unhandled request")
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }

        val config = ServiceClientConfig(issuer, "client", "secret")
        val delegate = KeycloakServiceTokenClient(httpClient, config)
        
        var currentTime = 1000L
        val client = CachingServiceTokenClient(delegate, clock = { currentTime })

        // First call
        val token1 = client.getToken()
        assertEquals("token-1", token1)
        assertEquals(1, callCount)

        // Second call - should be cached
        val token2 = client.getToken()
        assertEquals("token-1", token2)
        assertEquals(1, callCount)

        // Advance time near expiry
        currentTime += 280_000 // 280 seconds. expires_in was 300. buffer is 30. 
        // 1000 + 280_000 = 281_000. 
        // expiresAt = 1000 + 300 * 1000 = 301_000.
        // 301_000 - 281_000 = 20_000 (20 seconds left).
        // 20_000 < 30_000 (buffer), so it should refresh.

        val token3 = client.getToken()
        assertEquals("token-2", token3)
        assertEquals(2, callCount)
    }
}

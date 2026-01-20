package io.ktor.foodies.server.openid

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class ServiceTokenProviderTest {

    @Test
    fun `should fetch and cache token`() = runBlocking {
        val callCount = AtomicInteger(0)
        val mockEngine = MockEngine { request ->
            callCount.incrementAndGet()
            respond(
                content = """{"access_token":"test-token","expires_in":3600}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json()
            }
        }

        val provider = ServiceTokenProvider(
            httpClient = httpClient,
            clientId = "client-id",
            clientSecret = "client-secret",
            tokenEndpoint = "http://localhost/token"
        )

        // First call
        val result = provider.getToken("target-service") as TokenResult.Success
        assertEquals("test-token", result.token)
        assertEquals(1, callCount.get())

        // Second call (should be cached)
        val result2 = provider.getToken("target-service") as TokenResult.Success
        assertEquals("test-token", result2.token)
        assertEquals(1, callCount.get(), "Should use cached token")
    }

    @Test
    fun `should handle failure`() = runBlocking {
        val mockEngine = MockEngine { request ->
            respond(
                content = "Internal Server Error",
                status = HttpStatusCode.InternalServerError
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json()
            }
        }

        val provider = ServiceTokenProvider(
            httpClient = httpClient,
            clientId = "client-id",
            clientSecret = "client-secret",
            tokenEndpoint = "http://localhost/token"
        )

        val result = provider.getToken("target-service")
        assertTrue(result is TokenResult.Failed)
        assertEquals("Failed to acquire service token", (result as TokenResult.Failed).message)
    }
}

package io.ktor.foodies.server.openid

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.foodies.server.auth.KeycloakServiceTokenClient
import io.ktor.foodies.server.auth.ServiceClientConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ServiceTokenClientSpec {

    @Test
    fun `should get token from Keycloak`() = runBlocking {
        val issuer = "http://localhost:8080/realms/foodies"
        val mockEngine = MockEngine { request ->
            when (request.url.toString()) {
                "$issuer/.well-known/openid-configuration" -> {
                    respond(
                        content = """
                            {
                                "issuer": "$issuer",
                                "authorization_endpoint": "$issuer/protocol/openid-connect/auth",
                                "token_endpoint": "$issuer/protocol/openid-connect/token",
                                "jwks_uri": "$issuer/protocol/openid-connect/certs",
                                "end_session_endpoint": "$issuer/protocol/openid-connect/logout"
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                "$issuer/protocol/openid-connect/token" -> {
                    respond(
                        content = """
                            {
                                "access_token": "test-token",
                                "expires_in": 300,
                                "token_type": "Bearer"
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                else -> error("Unhandled request: ${request.url}")
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }

        val config = ServiceClientConfig(
            issuer = issuer,
            clientId = "test-client",
            clientSecret = "test-secret"
        )

        val client = KeycloakServiceTokenClient(httpClient, config)
        val token = client.getTokenWithScopes(listOf("basket:read"))

        assertEquals("test-token", token)
    }

    @Test
    fun `should get token with default scopes`() = runBlocking {
        val issuer = "http://localhost:8080/realms/foodies"
        val mockEngine = MockEngine { request ->
            when (request.url.toString()) {
                "$issuer/.well-known/openid-configuration" -> {
                    respond(
                        content = """
                            {
                                "issuer": "$issuer",
                                "authorization_endpoint": "$issuer/protocol/openid-connect/auth",
                                "token_endpoint": "$issuer/protocol/openid-connect/token",
                                "jwks_uri": "$issuer/protocol/openid-connect/certs",
                                "end_session_endpoint": "$issuer/protocol/openid-connect/logout"
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                "$issuer/protocol/openid-connect/token" -> {
                    // Check if scope is passed
                    val bodyText = (request.body as? io.ktor.client.request.forms.FormDataContent)?.formData?.get("scope")
                    if (bodyText == "basket:read") {
                        respond(
                            content = """
                                {
                                    "access_token": "token-with-scope",
                                    "expires_in": 300,
                                    "token_type": "Bearer"
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        )
                    } else {
                        respond(
                            content = """
                                {
                                    "access_token": "token-no-scope",
                                    "expires_in": 300,
                                    "token_type": "Bearer"
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        )
                    }
                }
                else -> error("Unhandled request: ${request.url}")
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }

        val config = ServiceClientConfig(
            issuer = issuer,
            clientId = "test-client",
            clientSecret = "test-secret",
            defaultScopes = listOf("basket:read")
        )

        val client = KeycloakServiceTokenClient(httpClient, config)
        val token = client.getToken()

        assertEquals("token-with-scope", token)
    }

    @Test
    fun `should throw exception on auth failure`() = runBlocking {
        val issuer = "http://localhost:8080"
        val mockEngine = MockEngine { request ->
            when (request.url.toString()) {
                "$issuer/.well-known/openid-configuration" -> {
                    respond(
                        content = """
                            {
                                "issuer": "$issuer",
                                "authorization_endpoint": "$issuer/auth",
                                "token_endpoint": "$issuer/token",
                                "jwks_uri": "$issuer/jwks",
                                "end_session_endpoint": "$issuer/logout"
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                "$issuer/token" -> {
                    respond(
                        content = """{"error": "invalid_client"}""",
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                else -> respond(
                    content = "Not Found",
                    status = HttpStatusCode.NotFound
                )
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }

        val config = ServiceClientConfig(
            issuer = issuer,
            clientId = "test-client",
            clientSecret = "test-secret"
        )

        val client = KeycloakServiceTokenClient(httpClient, config)

        val exception = assertFailsWith<IllegalStateException> {
            client.getToken()
        }
        assertTrue(exception.message!!.contains("Failed to obtain service token"))
        assertTrue(exception.message!!.contains("401 Unauthorized"))
        assertTrue(exception.message!!.contains("invalid_client"))
    }
}

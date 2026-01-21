package io.ktor.foodies.server.auth

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.foodies.server.test.keycloakContainer
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ServiceTokenClientIntegrationSpec {

    private val keycloak = keycloakContainer()

    @BeforeTest
    fun setup() {
        keycloak.start()
    }

    @AfterTest
    fun tearDown() {
        // We use withReuse(true), so we might not want to stop it, 
        // but for a single test run it's fine.
    }

    @Test
    fun `should get token from real Keycloak`() = runBlocking {
        val httpClient = HttpClient {
            install(ContentNegotiation) { json() }
        }

        val config = ServiceClientConfig(
            issuer = "${keycloak.authServerUrl}/realms/foodies-keycloak",
            clientId = "order-service",
            clientSecret = "order-service-secret"
        )

        val client = KeycloakServiceTokenClient(httpClient, config)
        val token = client.getToken()

        assertNotNull(token)
        println("Successfully retrieved token: ${token.take(10)}...")
    }

    @Test
    fun `should get token via CachingServiceTokenClient`() = runBlocking {
        val httpClient = HttpClient {
            install(ContentNegotiation) { json() }
        }

        val config = ServiceClientConfig(
            issuer = "${keycloak.authServerUrl}/realms/foodies-keycloak",
            clientId = "order-service",
            clientSecret = "order-service-secret"
        )

        val delegate = KeycloakServiceTokenClient(httpClient, config)
        val client = CachingServiceTokenClient(delegate)
        
        val token1 = client.getToken()
        assertNotNull(token1)
        
        val token2 = client.getToken()
        assertEquals(token1, token2)
        
        println("Successfully retrieved cached token")
    }
}

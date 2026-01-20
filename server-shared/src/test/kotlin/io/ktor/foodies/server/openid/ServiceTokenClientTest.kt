package io.ktor.foodies.server.openid

import dasniko.testcontainers.keycloak.KeycloakContainer
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import org.testcontainers.utility.MountableFile
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val serviceTokenClientSpec by testSuite(testConfig = TestConfig.testScope(true, timeout = 3.minutes)) {
    val realmFile = Paths.get(System.getProperty("user.dir")).parent.resolve("keycloak/realm.json")
    val keycloak = testFixture {
        KeycloakContainer("quay.io/keycloak/keycloak:26.0").apply {
            withProviderClassesFrom("target/classes")
            withCopyFileToContainer(
                MountableFile.forHostPath(realmFile),
                "/opt/keycloak/data/import/realm.json"
            )
            start()
        }
    } closeWith { stop() }

    suspend fun create(config: ServiceClientConfig): KeycloakServiceTokenClient {
        val httpClient = HttpClient(Apache5) { install(ContentNegotiation) { json() } }
        val openIdConfig = httpClient.discover(config.issuer)
        return KeycloakServiceTokenClient(httpClient, config, openIdConfig, Clock.System)
    }

    test("should obtain token via client credentials flow") {
        val kc = keycloak()
        val issuer = "${kc.authServerUrl}/realms/foodies-keycloak"

        val config = ServiceClientConfig(
            issuer = issuer,
            clientId = "order-service",
            clientSecret = "order-service-secret",
            defaultScopes = listOf("basket")
        )

        val httpClient = HttpClient(Apache5) { install(ContentNegotiation) { json() } }
        val openIdConfig = httpClient.discover(config.issuer)
        val tokenClient = KeycloakServiceTokenClient(httpClient, config, openIdConfig)
        val tokens = tokenClient.loadTokens()

        assertNotNull(tokens.accessToken)
        assertTrue(tokens.accessToken.isNotBlank())
    }

    test("should cache tokens and return same token on subsequent calls") {
        val kc = keycloak()
        val issuer = "${kc.authServerUrl}/realms/foodies-keycloak"

        val config = ServiceClientConfig(
            issuer = issuer,
            clientId = "order-service",
            clientSecret = "order-service-secret",
            listOf("basket")
        )

        val tokenClient = create(config)
        val firstTokens = tokenClient.loadTokens()
        val secondTokens = tokenClient.loadTokens()

        assertEquals(firstTokens.accessToken, secondTokens.accessToken)
    }

    test("should refresh token when requested") {
        val kc = keycloak()
        val issuer = "${kc.authServerUrl}/realms/foodies-keycloak"

        val config = ServiceClientConfig(
            issuer = issuer,
            clientId = "order-service",
            clientSecret = "order-service-secret",
            listOf("basket")
        )

        val tokenClient = create(config)
        val initialTokens = tokenClient.loadTokens()
        val refreshedTokens = tokenClient.refreshToken(initialTokens)

        assertNotNull(refreshedTokens.accessToken)
        assertTrue(refreshedTokens.accessToken.isNotBlank())
    }

    test("should throw ServiceTokenException for invalid credentials") {
        val kc = keycloak()
        val issuer = "${kc.authServerUrl}/realms/foodies-keycloak"

        val config = ServiceClientConfig(
            issuer = issuer,
            clientId = "order-service",
            clientSecret = "wrong-secret",
            listOf("basket")
        )

        val tokenClient = create(config)

        val exception = kotlin.runCatching { tokenClient.loadTokens() }.exceptionOrNull()
        assertTrue(exception is ServiceTokenException)
    }

    test("should work with basket-service client") {
        val kc = keycloak()
        val issuer = "${kc.authServerUrl}/realms/foodies-keycloak"

        val config = ServiceClientConfig(
            issuer = issuer,
            clientId = "basket-service",
            clientSecret = "basket-service-secret",
            listOf("order")
        )

        val tokenClient = create(config)
        val tokens = tokenClient.loadTokens()

        assertNotNull(tokens.accessToken)
        assertTrue(tokens.accessToken.isNotBlank())
    }

    test("should handle token expiry buffer") {
        val kc = keycloak()
        val issuer = "${kc.authServerUrl}/realms/foodies-keycloak"

        val config = ServiceClientConfig(
            issuer = issuer,
            clientId = "order-service",
            clientSecret = "order-service-secret",
            listOf("basket"),
            refreshBufferSeconds = 30.seconds
        )

        val tokenClient = create(config)
        val tokens = tokenClient.loadTokens()

        assertNotNull(tokens.accessToken)
    }
}

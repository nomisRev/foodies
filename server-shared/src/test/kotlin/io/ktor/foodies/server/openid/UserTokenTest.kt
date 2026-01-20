package io.ktor.foodies.server.openid

import com.auth0.jwt.JWT
import dasniko.testcontainers.keycloak.KeycloakContainer
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.testcontainers.utility.MountableFile
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@Serializable
private data class UserTokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("token_type")
    val tokenType: String
)

val userTokenSpec by testSuite("UserToken", testConfig = TestConfig.testScope(true, timeout = 3.minutes)) {
    val realmFile = Paths.get(System.getProperty("user.dir")).parent.resolve("keycloak/realm.json")
    val keycloak = testFixture {
        KeycloakContainer("quay.io/keycloak/keycloak:26.0").apply {
            withCopyFileToContainer(
                MountableFile.forHostPath(realmFile),
                "/opt/keycloak/data/import/realm.json"
            )
            start()
        }
    } closeWith { stop() }

    test("user login flow returns proper scopes and audience") {
        val kc = keycloak()
        val issuer = "${kc.authServerUrl}/realms/foodies-keycloak"
        val httpClient = HttpClient(Apache5) {
            install(ContentNegotiation) {
                json(kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                })
            }
        }
        val openIdConfig = httpClient.discover(issuer)

        val response = httpClient.submitForm(
            url = openIdConfig.tokenEndpoint,
            formParameters = Parameters.build {
                append("grant_type", "password")
                append("client_id", "foodies")
                append("client_secret", "foodies_client_secret")
                append("username", "food_lover")
                append("password", "password")
                append("scope", "openid roles")
            }
        )

        val tokenResponse = response.body<UserTokenResponse>()
        assertNotNull(tokenResponse.accessToken)

        val decoded = JWT.decode(tokenResponse.accessToken)
        println("Payload: ${String(java.util.Base64.getDecoder().decode(decoded.payload))}")
        
        // Check scopes
        val scopeClaim = decoded.getClaim("scope").asString()
        assertNotNull(scopeClaim, "Scope claim should not be null")
        val scopes = scopeClaim.split(" ").toSet()
        assertTrue(scopes.contains("basket:read"), "Should contain basket:read scope. Found: $scopes")
        assertTrue(scopes.contains("order:read"), "Should contain order:read scope. Found: $scopes")
        
        // Check audience
        val audience = decoded.audience
        assertTrue(audience.contains("basket-service"), "Should contain basket-service in audience. Found: $audience")
        assertTrue(audience.contains("order-service"), "Should contain order-service in audience. Found: $audience")
        assertTrue(audience.contains("foodies"), "Should contain foodies in audience. Found: $audience")
        
        // Check roles
        val realmAccess = decoded.getClaim("realm_access").asMap()
        @Suppress("UNCHECKED_CAST")
        val roles = (realmAccess["roles"] as List<String>)
        assertTrue(roles.contains("user"), "Should contain user role. Found: $roles")
    }
}

package io.ktor.foodies.server.test

import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.shared.TestRegistering
import io.ktor.foodies.server.auth.UserPrincipal
import io.ktor.foodies.server.openid.AuthSchemeKey
import io.ktor.foodies.server.openid.UserRole
import io.ktor.server.auth.oidc.OidcToken
import io.ktor.server.auth.oidc.OpenIdProviderMetadata
import io.ktor.server.auth.oidc.OpenIdTestKeys
import io.ktor.server.auth.oidc.openIdConnect
import io.ktor.server.auth.typesafe.withRoles
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.utils.io.ExperimentalKtorApi

const val TEST_ISSUER = "http://test-issuer"
const val TEST_AUDIENCE = "foodies"

private val DefaultOpenIdTestKeys = OpenIdTestKeys.rsa()

data class JwtConfig(
    val issuer: String = TEST_ISSUER,
    val audience: String = TEST_AUDIENCE,
    val keys: OpenIdTestKeys = DefaultOpenIdTestKeys,
)

fun createUserToken(
    config: JwtConfig = JwtConfig(),
    userId: String = "user-123",
    email: String = "test@example.com",
    name: String = "Test User",
    roles: List<String> = listOf("user")
): String = config.keys.accessToken(
    issuer = config.issuer,
    audience = config.audience,
) {
    subject = userId
    this.email = email
    this.name = name
    claim("realm_access", mapOf("roles" to roles))
}

@OptIn(ExperimentalKtorApi::class)
fun ApplicationTestBuilder.installTestAuth(config: JwtConfig = JwtConfig()) = application {
        val oidc = openIdConnect {}
        val provider = oidc.provider(
            name = "test",
            transformPrincipal = transform@{
                val accessToken = it as? OidcToken.Access ?: return@transform null
                if (accessToken.userInfo?.email == null) return@transform null
                UserPrincipal(accessToken)
            }
        ) {
            issuer = config.issuer
            metadata = OpenIdProviderMetadata(
                issuer = config.issuer,
                jwksUri = "${config.issuer}/jwks",
                tokenEndpoint = "${config.issuer}/token",
                authorizationEndpoint = "${config.issuer}/authorize",
            )
            jwt(config.keys)
            accessToken { audiences = setOf(config.audience) }
            bearer()
        }
        val authScheme = provider.bearer.withRoles { principal ->
            principal.roles.mapNotNull { UserRole.fromClaim(it) }.toSet()
        }
        attributes.put(AuthSchemeKey, authScheme)
}

@TestRegistering
fun TestSuite.authTest(
    name: String,
    config: JwtConfig = JwtConfig(),
    block: suspend context(Test.ExecutionScope) ApplicationTestBuilder.(JwtConfig) -> Unit,
) = testApplication(name) {
    installTestAuth(config)
    block(config)
}

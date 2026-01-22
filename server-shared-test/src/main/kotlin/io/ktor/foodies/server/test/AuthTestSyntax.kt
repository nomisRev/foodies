package io.ktor.foodies.server.test

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.Payload
import de.infix.testBalloon.framework.core.TestExecutionScope
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.shared.TestRegistering
import io.ktor.foodies.server.auth.ServicePrincipal
import io.ktor.foodies.server.auth.UserPrincipal
import io.ktor.foodies.server.test.testApplication
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.ApplicationTestBuilder
import java.util.*

const val TEST_JWT_SECRET = "test-jwt-secret"
const val TEST_ISSUER = "http://test-issuer"
const val TEST_AUDIENCE = "foodies"

data class JwtConfig(
    val algorithm: Algorithm = Algorithm.HMAC256(TEST_JWT_SECRET),
    val issuer: String = TEST_ISSUER,
    val audience: String = TEST_AUDIENCE
)

fun createUserToken(
    config: JwtConfig = JwtConfig(),
    userId: String = "user-123",
    email: String = "test@example.com",
    roles: List<String> = listOf("user")
): String = JWT.create()
    .withSubject(userId)
    .withClaim("email", email)
    .withClaim("realm_access", mapOf("roles" to roles))
    .withAudience(config.audience)
    .withIssuer(config.issuer)
    .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
    .sign(config.algorithm)

fun createServiceToken(
    config: JwtConfig = JwtConfig(),
    serviceAccountId: String = "service-account-test-service",
    clientId: String = "test-service",
    roles: List<String> = listOf("service:read")
): String = JWT.create()
    .withSubject(serviceAccountId)
    .withClaim("azp", clientId)
    .withClaim("realm_access", mapOf("roles" to roles))
    .withAudience(config.audience)
    .withIssuer(config.issuer)
    .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
    .sign(config.algorithm)

private fun Payload.roles(): Set<String> =
    (getClaim("realm_access")?.asMap()?.get("roles") as? List<*>)
        ?.filterIsInstance<String>()
        .orEmpty()
        .toSet()

fun ApplicationTestBuilder.installTestAuth(config: JwtConfig = JwtConfig()) = application {
    install(Authentication) {
        jwt("user") {
            verifier(JWT.require(config.algorithm).withIssuer(config.issuer).build())
            validate { credential: JWTCredential ->
                val claims = credential.payload.claims
                val email = claims["email"]?.asString()
                val authHeader = request.headers["Authorization"]?.removePrefix("Bearer ") ?: ""
                if (email != null) {
                    UserPrincipal(
                        userId = requireNotNull(credential.subject) { "Credential subject (userId) missing" },
                        email = email,
                        roles = credential.payload.roles(),
                        accessToken = authHeader
                    )
                } else null
            }
        }

        jwt("service") {
            verifier(JWT.require(config.algorithm).withIssuer(config.issuer).build())
            validate { credential: JWTCredential ->
                val payload = credential.payload.claims
                val clientId = payload["azp"]?.asString() ?: payload["client_id"]?.asString()
                if (clientId?.endsWith("-service") == true) {
                    ServicePrincipal(
                        serviceAccountId = requireNotNull(credential.subject) { "Credential subject (serviceAccountId) missing" },
                        clientId = clientId,
                        roles = credential.payload.roles()
                    )
                } else null
            }
        }
    }
}

@TestRegistering
fun TestSuite.authTest(
    name: String,
    config: JwtConfig = JwtConfig(),
    block: suspend context(TestExecutionScope) ApplicationTestBuilder.(JwtConfig) -> Unit,
) = testApplication(name) {
    installTestAuth(config)
    block(config)
}

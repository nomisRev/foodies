package io.ktor.foodies.basket

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.redis.testcontainers.RedisContainer
import com.sksamuel.cohort.HealthCheckRegistry
import de.infix.testBalloon.framework.core.TestExecutionScope
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.shared.TestRegistering
import io.ktor.foodies.server.auth.UserPrincipal
import io.ktor.foodies.server.test.testApplication
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.ApplicationTestBuilder
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.Dispatchers
import java.util.Date

private const val TEST_SECRET = "test-jwt-secret-for-end-to-end-testing"
private const val TEST_ISSUER = "test-issuer"
private const val TEST_AUDIENCE = "test-audience"

data class ServiceContext(
    val redisContainer: TestSuite.Fixture<RedisContainer>,
    val redisClient: TestSuite.Fixture<RedisClient>
)

fun TestSuite.serviceContext(): ServiceContext {
    val container = testFixture {
        RedisContainer("redis:7-alpine").apply { start() }
    }

    val redisClient = testFixture {
        RedisClient.create(container().redisURI)
    }
    return ServiceContext(container, redisClient)
}

data class BasketTestModule(
    val basketService: BasketService,
    val menuClient: InMemoryMenuClient
)

fun createTestToken(
    subject: String,
    issuer: String = TEST_ISSUER,
    audience: String = TEST_AUDIENCE,
): String = JWT.create()
    .withSubject(subject)
    .withIssuer(issuer)
    .withAudience(audience)
    .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
    .sign(Algorithm.HMAC256(TEST_SECRET))

@OptIn(ExperimentalLettuceCoroutinesApi::class)
@TestRegistering
context(ctx: ServiceContext)
fun TestSuite.testBasketService(
    name: String,
    block: suspend context(TestExecutionScope) ApplicationTestBuilder.(module: BasketTestModule) -> Unit
) {
    testApplication(name) {
        ctx.redisClient().connect().use { connection ->
            val menuClient = InMemoryMenuClient()
            val repository = RedisBasketRepository(connection.coroutines())
            val service = BasketServiceImpl(repository, menuClient)
            val module = BasketModule(
                basketService = service,
                consumers = emptyList(),
                readinessCheck = HealthCheckRegistry(Dispatchers.IO)
            )

            application {
                install(Authentication) {
                    jwt("user") {
                        verifier(
                            JWT.require(Algorithm.HMAC256(TEST_SECRET))
                                .withIssuer(TEST_ISSUER)
                                .withAudience(TEST_AUDIENCE)
                                .build()
                        )
                        validate { credential ->
                            val payload = credential.payload
                            val subject = payload.subject
                            if (subject != null) {
                                val authHeader = request.headers["Authorization"]?.removePrefix("Bearer ") ?: ""
                                UserPrincipal(
                                    userId = subject,
                                    email = null,
                                    roles = emptySet(),
                                    accessToken = authHeader
                                )
                            } else null
                        }
                    }
                }
                app(module)
            }
            block(BasketTestModule(module.basketService, menuClient))
            connection.sync().flushall()
        }
    }
}

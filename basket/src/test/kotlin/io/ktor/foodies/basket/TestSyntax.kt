package io.ktor.foodies.basket

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.redis.testcontainers.RedisContainer
import com.sksamuel.cohort.HealthCheckRegistry
import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.TestFixture
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.shared.TestRegistering
import io.ktor.foodies.server.auth.UserPrincipal
import io.ktor.foodies.server.test.installTestAuth
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
    val redisContainer: TestFixture<RedisContainer>,
    val redisClient: TestFixture<RedisClient>
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

@OptIn(ExperimentalLettuceCoroutinesApi::class)
@TestRegistering
context(ctx: ServiceContext)
fun TestSuite.testBasketService(
    name: String,
    block: suspend context(Test.ExecutionScope) ApplicationTestBuilder.(module: BasketTestModule) -> Unit
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

            installTestAuth()
            application { app(module) }
            block(BasketTestModule(module.basketService, menuClient))
            connection.sync().flushall()
        }
    }
}

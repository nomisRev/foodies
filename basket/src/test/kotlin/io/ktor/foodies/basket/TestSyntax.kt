package io.ktor.foodies.basket

import com.redis.testcontainers.RedisContainer
import com.sksamuel.cohort.HealthCheckRegistry
import de.infix.testBalloon.framework.core.TestExecutionScope
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.shared.TestRegistering
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.foodies.server.openid.Auth
import io.ktor.foodies.server.openid.security
import io.ktor.foodies.server.test.KeycloakAuthContext
import io.ktor.foodies.server.test.KeycloakContainer
import io.ktor.foodies.server.test.keycloakContainer
import io.ktor.foodies.server.test.testApplication
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.MountableFile
import java.nio.file.Paths

data class ServiceContext(
    val redisContainer: TestSuite.Fixture<RedisContainer>,
    val redisClient: TestSuite.Fixture<RedisClient>,
    val keycloak: TestSuite.Fixture<KeycloakContainer>
)

fun TestSuite.serviceContext(): ServiceContext {
    val fixture = testFixture {
        val redis = RedisContainer("redis:7-alpine")
        val keycloak = KeycloakContainer()
        Startables.deepStart(redis, keycloak).await()
        Pair(redis, keycloak)
    }
    val redisContainer = testFixture { fixture().first }
    val keycloakContainer = testFixture { fixture().second }
    val redisClient = testFixture { RedisClient.create(fixture().first.redisURI) }
    return ServiceContext(redisContainer, redisClient, keycloakContainer)
}

data class BasketTestModule(
    val basketService: BasketService,
    val menuClient: InMemoryMenuClient,
    val authContext: KeycloakAuthContext
)

@OptIn(ExperimentalLettuceCoroutinesApi::class)
@TestRegistering
context(ctx: ServiceContext)
fun TestSuite.testBasketService(
    name: String,
    block: suspend context(TestExecutionScope) ApplicationTestBuilder.(module: BasketTestModule) -> Unit
) {
    testApplication(name) {
        ctx.redisClient().connect().use { connection ->
            val keycloakInstance = ctx.keycloak()
            val menuClient = InMemoryMenuClient()
            val repository = RedisBasketRepository(connection.coroutines())
            val service = BasketServiceImpl(repository, menuClient)
            val module = BasketModule(
                basketService = service,
                consumers = emptyList(),
                readinessCheck = HealthCheckRegistry(Dispatchers.IO)
            )
            val client = HttpClient(Apache5) {
                install(ContentNegotiation) { json() }
            }

            application {
                val auth = Auth(
                    issuer = "${keycloakInstance.authServerUrl}/realms/foodies-keycloak",
                    serviceAudience = "basket-service",
                    userAudience = "foodies"
                )
                security(auth, client)
                app(module)
            }
            block(BasketTestModule(module.basketService, menuClient, KeycloakAuthContext(keycloakInstance, client)))
            connection.sync().flushall()
        }
    }
}

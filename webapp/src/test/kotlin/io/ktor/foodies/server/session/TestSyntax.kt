package io.ktor.foodies.server.session

import com.redis.testcontainers.RedisContainer
import de.infix.testBalloon.framework.core.TestExecutionScope
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.shared.TestRegistering
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.coroutines

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

@OptIn(ExperimentalLettuceCoroutinesApi::class)
@TestRegistering
context(ctx: ServiceContext)
fun TestSuite.testRedis(
    name: String,
    ttlSeconds: Long = 3600,
    block: suspend context(TestExecutionScope) (storage: RedisSessionStorage) -> Unit
) = test(name) {
    ctx.redisClient().connect().use { connection ->
        block(RedisSessionStorage(connection.coroutines(), ttlSeconds))
        connection.sync().flushall()
    }
}

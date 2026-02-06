package io.ktor.foodies.server.session

import com.redis.testcontainers.RedisContainer
import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.TestFixture
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.shared.TestRegistering
import io.ktor.foodies.server.security.RedisSessionStorage
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.coroutines

data class RedisContext(
    val redisContainer: TestFixture<RedisContainer>,
    val redisClient: TestFixture<RedisClient>
)

fun TestSuite.redisContext(): RedisContext {
    val container = testFixture {
        RedisContainer("redis:7-alpine").apply { start() }
    }

    val redisClient = testFixture {
        RedisClient.create(container().redisURI)
    }
    return RedisContext(container, redisClient)
}

@OptIn(ExperimentalLettuceCoroutinesApi::class)
@TestRegistering
context(ctx: RedisContext)
fun TestSuite.testRedis(
    name: String,
    ttlSeconds: Long = 3600,
    block: suspend context(Test.ExecutionScope) (storage: RedisSessionStorage) -> Unit
) = test(name) {
    ctx.redisClient().connect().use { connection ->
        block(RedisSessionStorage(connection.coroutines(), ttlSeconds))
        connection.sync().flushall()
    }
}

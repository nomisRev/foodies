package io.ktor.foodies.server.security

import io.ktor.foodies.server.Config
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.sessions.SessionStorage
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines

data class SecurityModule(
    val sessionStorage: SessionStorage,
    val redisConnection: StatefulRedisConnection<String, String>
)

@OptIn(ExperimentalLettuceCoroutinesApi::class)
fun Application.securityModule(config: Config.RedisSession): SecurityModule {
    val auth = if (config.password.isNotBlank()) ":${config.password}@" else ""
    val client = RedisClient.create("redis://$auth${config.host}:${config.port}")
    val connection = client.connect()
    monitor.subscribe(ApplicationStopped) {
        connection.close()
        client.shutdown()
    }

    return SecurityModule(
        sessionStorage = RedisSessionStorage(connection.coroutines(), config.ttlSeconds),
        redisConnection = connection
    )
}

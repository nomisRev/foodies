package io.ktor.foodies.server.security

import io.ktor.server.sessions.SessionStorage
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisSessionStorage(
    private val redis: RedisCoroutinesCommands<String, String>,
    private val ttlSeconds: Long = 3600,
) : SessionStorage {

    private fun sessionKey(id: String): String = "/session/$id"

    override suspend fun write(id: String, value: String) {
        redis.setex(sessionKey(id), ttlSeconds, value)
    }

    override suspend fun read(id: String): String {
        return redis.get(sessionKey(id)) ?: throw NoSuchElementException("Session $id not found")
    }

    override suspend fun invalidate(id: String) {
        redis.del(sessionKey(id))
    }
}

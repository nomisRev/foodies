package io.ktor.foodies.server.session

import kotlinx.serialization.Serializable

@Serializable
data class RedisSessionConfig(
    val host: String,
    val port: Int,
    val password: String = "",
    val ttlSeconds: Long = 3600
)

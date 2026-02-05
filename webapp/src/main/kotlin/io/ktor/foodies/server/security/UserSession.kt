package io.ktor.foodies.server.security

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class UserSession(
    val idToken: String,
    val accessToken: String,
    val expiresIn: Long,
    val refreshToken: String? = null,
    val expiresAt: Instant? = null
)
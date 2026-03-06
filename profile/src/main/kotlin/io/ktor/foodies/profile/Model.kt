package io.ktor.foodies.profile

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: Long,
    val subject: String,
    val email: String,
    val firstName: String,
    val lastName: String,
)

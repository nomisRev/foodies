package io.ktor.foodies.keycloak

import kotlinx.serialization.Serializable

@Serializable
internal data class NewUserEvent(
    val subject: String,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
)
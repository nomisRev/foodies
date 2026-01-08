package io.ktor.foodies.user.event

import kotlinx.serialization.Serializable

@Serializable
sealed interface UserEvent {
    val subject: String

    @Serializable
    data class Registration(
        override val subject: String,
        val email: String,
        val firstName: String,
        val lastName: String
    ) : UserEvent

    @Serializable
    data class Delete(
        override val subject: String,
    ) : UserEvent
}

package io.ktor.foodies.keycloak

import io.ktor.foodies.user.event.UserEvent
import org.keycloak.events.Event
import org.keycloak.events.EventType
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("UserEventTransformer")

fun Event.toUserEvent(): UserEvent? =
    when (type) {
        EventType.REGISTER -> toNewUserEvent()
        else -> null
    }

private fun Event.toNewUserEvent(): UserEvent.Registration {
    val email = details?.get("email")
    val firstName = details?.get("first_name")
    val lastName = details?.get("last_name")
    return if (userId == null || userId.isEmpty() || email == null || firstName == null || lastName == null) {
        throw IllegalStateException("Missing required fields for registration event: userId=$userId email=$email firstName=$firstName, lastName=$lastName")
    } else {
        UserEvent.Registration(subject = userId, email = email, firstName = firstName, lastName = lastName)
    }
}

package io.ktor.foodies.keycloak

import io.ktor.foodies.user.event.UserEvent
import org.keycloak.events.Event
import org.keycloak.events.EventType

fun Event.toUserEvent(): UserEvent? =
    when (type) {
        EventType.REGISTER -> toNewUserEvent()
        EventType.UPDATE_PROFILE -> toUpdateProfileEvent()
        EventType.DELETE_ACCOUNT -> toDeleteEvent()
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

private fun Event.toUpdateProfileEvent(): UserEvent.UpdateProfile {
    val email = details?.get("email")
    val firstName = details?.get("first_name")
    val lastName = details?.get("last_name")
    return if (userId == null || userId.isEmpty() || email == null || firstName == null || lastName == null) {
        throw IllegalStateException("Missing required fields for profile update event: userId=$userId email=$email firstName=$firstName, lastName=$lastName")
    } else {
        UserEvent.UpdateProfile(subject = userId, email = email, firstName = firstName, lastName = lastName)
    }
}

private fun Event.toDeleteEvent(): UserEvent.Delete {
    return if (userId.isNullOrEmpty()) {
        throw IllegalStateException("Missing required fields for delete event: userId=$userId")
    } else {
        UserEvent.Delete(subject = userId)
    }
}

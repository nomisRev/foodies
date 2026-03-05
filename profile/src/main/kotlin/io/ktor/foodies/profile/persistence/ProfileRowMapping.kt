package io.ktor.foodies.profile.persistence

import io.ktor.foodies.profile.Profile
import org.jetbrains.exposed.v1.core.ResultRow

fun ResultRow.toProfile() = Profile(
    id = this[ProfileTable.id].value,
    subject = this[ProfileTable.subject],
    email = this[ProfileTable.email],
    firstName = this[ProfileTable.firstName],
    lastName = this[ProfileTable.lastName],
)

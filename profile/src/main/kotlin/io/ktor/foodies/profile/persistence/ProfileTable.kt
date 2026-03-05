package io.ktor.foodies.profile.persistence

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

object ProfileTable : LongIdTable("profiles") {
    val subject = varchar("subject", 255).uniqueIndex()
    val email = varchar("email", 255)
    val firstName = varchar("first_name", 255)
    val lastName = varchar("last_name", 255)
}

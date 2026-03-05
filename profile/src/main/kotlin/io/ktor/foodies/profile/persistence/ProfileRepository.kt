package io.ktor.foodies.profile.persistence

import io.ktor.foodies.profile.Profile
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnoreAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert

interface ProfileRepository {
    fun findBySubject(subject: String): Profile?
    fun insertOrIgnore(subject: String, email: String, firstName: String, lastName: String): Long?
    fun upsert(subject: String, email: String, firstName: String, lastName: String)
    fun deleteBySubject(subject: String): Boolean
}

class ExposedProfileRepository(private val database: Database) : ProfileRepository {
    override fun findBySubject(subject: String): Profile? = transaction(database) {
        ProfileTable.selectAll()
            .where { ProfileTable.subject eq subject }
            .map { it.toProfile() }
            .singleOrNull()
    }

    override fun insertOrIgnore(subject: String, email: String, firstName: String, lastName: String): Long? =
        transaction(database) {
            ProfileTable.insertIgnoreAndGetId { row ->
                row[ProfileTable.subject] = subject
                row[ProfileTable.email] = email
                row[ProfileTable.firstName] = firstName
                row[ProfileTable.lastName] = lastName
            }
        }?.value

    override fun upsert(subject: String, email: String, firstName: String, lastName: String) {
        transaction(database) {
            ProfileTable.upsert(ProfileTable.subject) { row ->
                row[ProfileTable.subject] = subject
                row[ProfileTable.email] = email
                row[ProfileTable.firstName] = firstName
                row[ProfileTable.lastName] = lastName
            }
        }
    }

    override fun deleteBySubject(subject: String): Boolean = transaction(database) {
        ProfileTable.deleteWhere { ProfileTable.subject eq subject } == 1
    }
}

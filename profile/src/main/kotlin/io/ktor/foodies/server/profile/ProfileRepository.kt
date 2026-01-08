package io.ktor.foodies.server.profile

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnoreAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert

@Serializable
data class Profile(
    val id: Long,
    val subject: String,
    val email: String,
    val firstName: String,
    val lastName: String,
)

interface ProfileRepository {
    fun findBySubject(subject: String): Profile?
    fun insertOrIgnore(subject: String, email: String, firstName: String, lastName: String): Long?
    fun upsert(subject: String, email: String, firstName: String, lastName: String)
    fun deleteBySubject(subject: String): Boolean
}

object ProfileTable : LongIdTable("profiles") {
    val subject = varchar("subject", 255).uniqueIndex()
    val email = varchar("email", 255)
    val firstName = varchar("first_name", 255)
    val lastName = varchar("last_name", 255)
}

class ExposedProfileRepository(private val database: Database) : ProfileRepository {
    override fun findBySubject(subject: String): Profile? = transaction(database) {
        ProfileTable.selectAll()
            .where { ProfileTable.subject eq subject }
            .map { it.toCustomer() }
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

    private fun ResultRow.toCustomer() = Profile(
        id = this[ProfileTable.id].value,
        subject = this[ProfileTable.subject],
        email = this[ProfileTable.email],
        firstName = this[ProfileTable.firstName],
        lastName = this[ProfileTable.lastName],
    )
}

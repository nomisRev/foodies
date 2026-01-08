package io.ktor.foodies.server.customers

import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.server.DataSource
import io.ktor.foodies.server.profile.ExposedProfileRepository
import io.ktor.foodies.server.test.postgresDataSource
import org.flywaydb.core.Flyway
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

val customerRepositorySpec by testSuite {
    val database = migratedPostgresDataSource()
    val repository = testFixture { ExposedProfileRepository(database().database) }

    test("create profile from InsertProfile") {
        val profile = repository().insertOrIgnore(
            subject = "keycloak-123",
            email = "test@example.com",
            firstName = "Test",
            lastName = "User",
        )

        assertNotNull(profile.id)
        assertEquals("keycloak-123", profile.subject)
        assertEquals("test@example.com", profile.email)
        assertEquals("Test", profile.firstName)
        assertEquals("User", profile.lastName)
    }

    test("find profile by subject") {
        repository().insertOrIgnore(
            subject = "keycloak-456",
            email = "find@example.com",
            firstName = "Find",
            lastName = "User",
        )

        val found = repository().findBySubject("keycloak-456")

        assertNotNull(found)
        assertEquals("keycloak-456", found.subject)
    }

    test("findBySubject returns null for non-existent profile") {
        val found = repository().findBySubject("non-existent")
        assertNull(found)
    }
}

fun TestSuite.migratedPostgresDataSource(): TestSuite.Fixture<DataSource> =
    testFixture {
        val ds = postgresDataSource()()
        Flyway.configure().dataSource(ds.hikari).load().migrate()
        ds
    }

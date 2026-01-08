package io.ktor.foodies.server.profile

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.server.customers.migratedPostgresDataSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

val profileRepositorySpec by testSuite {
    val dataSource = migratedPostgresDataSource()
    val repository = testFixture { ExposedProfileRepository(dataSource().database) }

    test("insertOrIgnore inserts a new profile and returns its id") {
        val id = repository().insertOrIgnore(
            subject = "profile-insert",
            email = "insert@example.com",
            firstName = "Insert",
            lastName = "Test",
        )

        val profile = repository().findBySubject("profile-insert")

        assertEquals(
            Profile(
                id = requireNotNull(id),
                subject = "profile-insert",
                email = "insert@example.com",
                firstName = "Insert",
                lastName = "Test",
            ),
            profile
        )
    }

    test("insertOrIgnore returns null for duplicate subjects") {
        repository().insertOrIgnore(
            subject = "duplicate-subject",
            email = "first@example.com",
            firstName = "First",
            lastName = "User",
        )

        val secondId = repository().insertOrIgnore(
            subject = "duplicate-subject",
            email = "second@example.com",
            firstName = "Second",
            lastName = "User",
        )

        assertNull(secondId)
    }

    test("insertOrIgnore keeps the original profile data when ignoring duplicates") {
        val firstId = repository().insertOrIgnore(
            subject = "duplicate-subject-data",
            email = "first@example.com",
            firstName = "First",
            lastName = "User",
        )

        repository().insertOrIgnore(
            subject = "duplicate-subject-data",
            email = "second@example.com",
            firstName = "Second",
            lastName = "User",
        )

        val profile = repository().findBySubject("duplicate-subject-data")

        assertEquals(
            Profile(
                assertNotNull(firstId),
                subject = "duplicate-subject-data",
                email = "first@example.com",
                firstName = "First",
                lastName = "User",
            ),
            profile
        )
    }

    test("upsert inserts a new profile") {
        repository().upsert(
            subject = "profile-upsert-insert",
            email = "upsert-insert@example.com",
            firstName = "UpsertInsert",
            lastName = "Test",
        )

        val profile = repository().findBySubject("profile-insert")
        assertEquals("profile-upsert-insert", profile?.subject)
        assertEquals("upsert-insert@example.com", profile?.email)
        assertEquals("UpsertInsert", profile?.firstName)
        assertEquals("Test", profile?.lastName)
    }

    test("upsert updates an existing profile") {
        val subject = "updatable-subject"
        repository().insertOrIgnore(
            subject = subject,
            email = "old@example.com",
            firstName = "Old",
            lastName = "Name",
        )

        repository().upsert(
            subject = subject,
            email = "new@example.com",
            firstName = "New",
            lastName = "Name",
        )

        val profile = repository().findBySubject(subject)

        assertEquals(
            Profile(
                id = requireNotNull(profile?.id),
                subject = subject,
                email = "new@example.com",
                firstName = "New",
                lastName = "Name",
            ),
            profile
        )
    }

    test("findBySubject returns null for missing profile") {
        val profile = repository().findBySubject("missing-subject")
        assertNull(profile)
    }

    test("deleteBySubject removes an existing profile") {
        val subject = "deletable-subject"
        repository().insertOrIgnore(
            subject = subject,
            email = "delete@example.com",
            firstName = "Delete",
            lastName = "Me",
        )

        val deleted = repository().deleteBySubject(subject)
        val profile = repository().findBySubject(subject)

        assertEquals(true, deleted)
        assertNull(profile)
    }

    test("deleteBySubject returns false when profile is absent") {
        val deleted = repository().deleteBySubject("unknown-subject")
        assertEquals(false, deleted)
    }
}
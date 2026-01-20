package io.ktor.foodies.server.openid

import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

val userPrincipalSpec by testSuite {

    test("hasScope returns true for existing scope") {
        val principal = UserPrincipal(
            userId = "test-user",
            scopes = setOf("read", "write")
        )

        assertTrue(principal.hasScope("read"))
        assertTrue(principal.hasScope("write"))
        assertFalse(principal.hasScope("admin"))
    }

    test("hasRole returns true for existing role") {
        val principal = UserPrincipal(
            userId = "test-user",
            roles = setOf("user", "admin")
        )

        assertTrue(principal.hasRole("user"))
        assertTrue(principal.hasRole("admin"))
        assertFalse(principal.hasRole("super-admin"))
    }

    test("hasAllScopes checks all scopes") {
        val principal = UserPrincipal(
            userId = "test-user",
            scopes = setOf("read", "write", "delete")
        )

        assertTrue(principal.hasAllScopes("read", "write"))
        assertTrue(principal.hasAllScopes("read"))
        assertFalse(principal.hasAllScopes("read", "admin"))
    }

    test("hasAnyScope checks any scope") {
        val principal = UserPrincipal(
            userId = "test-user",
            scopes = setOf("read", "write")
        )

        assertTrue(principal.hasAnyScope("read", "admin"))
        assertTrue(principal.hasAnyScope("write"))
        assertFalse(principal.hasAnyScope("admin", "super"))
    }

    test("UserPrincipal with null optional fields") {
        val principal = UserPrincipal(
            userId = "test-user"
        )

        assertEquals("test-user", principal.userId)
        assertEquals(null, principal.email)
        assertEquals(null, principal.name)
        assertEquals(emptySet(), principal.roles)
        assertEquals(emptySet(), principal.scopes)
    }

    test("UserPrincipal with all fields populated") {
        val principal = UserPrincipal(
            userId = "user-123",
            email = "test@example.com",
            name = "Test User",
            roles = setOf("user", "admin"),
            scopes = setOf("openid", "profile", "email")
        )

        assertEquals("user-123", principal.userId)
        assertEquals("test@example.com", principal.email)
        assertEquals("Test User", principal.name)
        assertEquals(setOf("user", "admin"), principal.roles)
        assertEquals(setOf("openid", "profile", "email"), principal.scopes)
    }
}


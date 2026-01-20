package io.ktor.foodies.server.openid

import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

val userPrincipalSpec by testSuite {

    test("hasScope returns true for existing scope") {
        val principal = UserPrincipal(
            userId = "test-user",
            scopes = setOf("read", "write"),
            roles = emptySet()
        )

        assertTrue("read" in principal.scopes)
        assertTrue("write" in principal.scopes)
        assertFalse("admin" in principal.scopes)
    }

    test("hasRole returns true for existing role") {
        val principal = UserPrincipal(
            userId = "test-user",
            roles = setOf("user", "admin"),
            scopes = emptySet()
        )

        assertTrue("user" in principal.roles)
        assertTrue("admin" in principal.roles)
        assertFalse("super-admin" in principal.roles)
    }

    test("hasAllScopes checks all scopes") {
        val principal = UserPrincipal(
            userId = "test-user",
            scopes = setOf("read", "write", "delete"),
            roles = emptySet()
        )

        val scopes = arrayOf("read", "write")
        assertTrue(scopes.all { it in principal.scopes })
        val scopes1 = arrayOf("read")
        assertTrue(scopes1.all { it in principal.scopes })
        val scopes2 = arrayOf("read", "admin")
        assertFalse(scopes2.all { it in principal.scopes })
    }

    test("hasAnyScope checks any scope") {
        val principal = UserPrincipal(
            userId = "test-user",
            scopes = setOf("read", "write"),
            roles = emptySet()
        )

        val scopes = arrayOf("read", "admin")
        assertTrue(scopes.any { it in principal.scopes })
        val scopes1 = arrayOf("write")
        assertTrue(scopes1.any { it in principal.scopes })
        val scopes2 = arrayOf("admin", "super")
        assertFalse(scopes2.any { it in principal.scopes })
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


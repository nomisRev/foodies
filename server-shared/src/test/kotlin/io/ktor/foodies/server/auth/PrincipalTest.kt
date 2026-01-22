package io.ktor.foodies.server.auth

import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

val principalSpec by testSuite {
    test("UserPrincipal construction with all fields") {
        val principal = UserPrincipal(
            userId = "user-123",
            email = "test@example.com",
            roles = setOf("user", "admin"),
            accessToken = "token-abc"
        )

        assertEquals("user-123", principal.userId)
        assertEquals("test@example.com", principal.email)
        assertEquals(setOf("user", "admin"), principal.roles)
        assertEquals("token-abc", principal.accessToken)
    }

    test("UserPrincipal with null email") {
        val principal = UserPrincipal(
            userId = "user-456",
            email = null,
            roles = setOf("user"),
            accessToken = "token-xyz"
        )

        assertEquals("user-456", principal.userId)
        assertNull(principal.email)
        assertEquals(setOf("user"), principal.roles)
        assertEquals("token-xyz", principal.accessToken)
    }

    test("UserPrincipal with empty roles set") {
        val principal = UserPrincipal(
            userId = "user-789",
            email = "empty@example.com",
            roles = emptySet(),
            accessToken = "token-empty"
        )

        assertEquals("user-789", principal.userId)
        assertEquals("empty@example.com", principal.email)
        assertTrue(principal.roles.isEmpty())
        assertEquals("token-empty", principal.accessToken)
    }

    test("ServicePrincipal construction with all fields") {
        val principal = ServicePrincipal(
            serviceAccountId = "service-123",
            clientId = "client-abc",
            roles = setOf("service", "admin")
        )

        assertEquals("service-123", principal.serviceAccountId)
        assertEquals("client-abc", principal.clientId)
        assertEquals(setOf("service", "admin"), principal.roles)
        assertNull(principal.userContext)
    }

    test("ServicePrincipal with null userContext") {
        val principal = ServicePrincipal(
            serviceAccountId = "service-456",
            clientId = "client-xyz",
            roles = setOf("service"),
            userContext = null
        )

        assertEquals("service-456", principal.serviceAccountId)
        assertEquals("client-xyz", principal.clientId)
        assertEquals(setOf("service"), principal.roles)
        assertNull(principal.userContext)
    }

    test("ServicePrincipal with non-null userContext") {
        val userContext = UserPrincipal(
            userId = "user-999",
            email = "context@example.com",
            roles = setOf("user"),
            accessToken = "user-token"
        )

        val principal = ServicePrincipal(
            serviceAccountId = "service-789",
            clientId = "client-def",
            roles = setOf("service"),
            userContext = userContext
        )

        assertEquals("service-789", principal.serviceAccountId)
        assertEquals("client-def", principal.clientId)
        assertEquals(setOf("service"), principal.roles)
        assertEquals(userContext, principal.userContext)
        assertEquals("user-999", principal.userContext?.userId)
        assertEquals("context@example.com", principal.userContext?.email)
    }
}

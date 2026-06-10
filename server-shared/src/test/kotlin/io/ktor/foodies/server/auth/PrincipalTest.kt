package io.ktor.foodies.server.auth

import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertNull

val principalSpec by testSuite {
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
}

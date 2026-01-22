package io.ktor.foodies.server.auth

import de.infix.testBalloon.framework.core.testSuite
import kotlin.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val serviceTokenSpec by testSuite {
    test("ServiceToken is not expired when time is before expiry") {
        val token = ServiceToken(
            accessToken = "token-123",
            expiresAt = Clock.System.now() + 5.minutes
        )

        assertFalse(token.isExpired())
    }

    test("ServiceToken is expired when time is past expiry") {
        val token = ServiceToken(
            accessToken = "token-456",
            expiresAt = Clock.System.now() - 1.seconds
        )

        assertTrue(token.isExpired())
    }

    test("ServiceToken is expired when within buffer window") {
        val token = ServiceToken(
            accessToken = "token-789",
            expiresAt = Clock.System.now() + 29.seconds
        )

        assertTrue(token.isExpired(bufferSeconds = 30))
    }

    test("ServiceToken is not expired when beyond buffer window") {
        val token = ServiceToken(
            accessToken = "token-abc",
            expiresAt = Clock.System.now() + 31.seconds
        )

        assertFalse(token.isExpired(bufferSeconds = 30))
    }

    test("ServiceToken uses default 30 second buffer") {
        val token = ServiceToken(
            accessToken = "token-def",
            expiresAt = Clock.System.now() + 25.seconds
        )

        assertTrue(token.isExpired())
    }

    test("ServiceToken has Bearer as default token type") {
        val token = ServiceToken(
            accessToken = "token-ghi",
            expiresAt = Clock.System.now() + 5.minutes
        )

        assertEquals("Bearer", token.tokenType)
    }

    test("ServiceToken can have custom token type") {
        val token = ServiceToken(
            accessToken = "token-jkl",
            expiresAt = Clock.System.now() + 5.minutes,
            tokenType = "Custom"
        )

        assertEquals("Custom", token.tokenType)
    }

    test("ServiceToken equality is based on all fields") {
        val expiresAt = Clock.System.now() + 5.minutes
        val token1 = ServiceToken(
            accessToken = "token-mno",
            expiresAt = expiresAt,
            tokenType = "Bearer"
        )
        val token2 = ServiceToken(
            accessToken = "token-mno",
            expiresAt = expiresAt,
            tokenType = "Bearer"
        )

        assertEquals(token1, token2)
    }
}

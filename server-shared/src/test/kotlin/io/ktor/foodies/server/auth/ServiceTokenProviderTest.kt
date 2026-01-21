package io.ktor.foodies.server.auth

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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

val serviceTokenProviderSpec by testSuite {
    test("withServiceAuth establishes ServiceAuth context without user token") {
        runTest {
            val provider = object : ServiceTokenProvider {
                override suspend fun getToken(): ServiceToken {
                    return ServiceToken(
                        accessToken = "test-service-token",
                        expiresAt = Clock.System.now() + 5.minutes
                    )
                }
            }

            var capturedContext: AuthContext? = null

            provider.withServiceAuth {
                capturedContext = currentCoroutineContext()[AuthContext]
            }

            assertNotNull(capturedContext)
            assertIs<AuthContext.ServiceAuth>(capturedContext)
            val serviceAuth = capturedContext as AuthContext.ServiceAuth
            assertEquals("test-service-token", serviceAuth.serviceToken)
            assertEquals(null, serviceAuth.userToken)
        }
    }

    test("withServiceAuth establishes ServiceAuth context with user token") {
        runTest {
            val provider = object : ServiceTokenProvider {
                override suspend fun getToken(): ServiceToken {
                    return ServiceToken(
                        accessToken = "test-service-token",
                        expiresAt = Clock.System.now() + 5.minutes
                    )
                }
            }

            var capturedContext: AuthContext? = null

            provider.withServiceAuth(userToken = "user-jwt-123") {
                capturedContext = currentCoroutineContext()[AuthContext]
            }

            assertNotNull(capturedContext)
            assertIs<AuthContext.ServiceAuth>(capturedContext)
            val serviceAuth = capturedContext as AuthContext.ServiceAuth
            assertEquals("test-service-token", serviceAuth.serviceToken)
            assertEquals("user-jwt-123", serviceAuth.userToken)
        }
    }

    test("withServiceAuth returns block result") {
        runTest {
            val provider = object : ServiceTokenProvider {
                override suspend fun getToken(): ServiceToken {
                    return ServiceToken(
                        accessToken = "test-token",
                        expiresAt = Clock.System.now() + 5.minutes
                    )
                }
            }

            val result = provider.withServiceAuth {
                "block-result"
            }

            assertEquals("block-result", result)
        }
    }

    test("withServiceAuth calls getToken before executing block") {
        runTest {
            var getTokenCalled = false
            val provider = object : ServiceTokenProvider {
                override suspend fun getToken(): ServiceToken {
                    getTokenCalled = true
                    return ServiceToken(
                        accessToken = "test-token",
                        expiresAt = Clock.System.now() + 5.minutes
                    )
                }
            }

            assertFalse(getTokenCalled)

            provider.withServiceAuth {
                assertTrue(getTokenCalled)
            }
        }
    }

    test("withServiceAuth propagates exceptions from block") {
        runTest {
            val provider = object : ServiceTokenProvider {
                override suspend fun getToken(): ServiceToken {
                    return ServiceToken(
                        accessToken = "test-token",
                        expiresAt = Clock.System.now() + 5.minutes
                    )
                }
            }

            val exception = try {
                provider.withServiceAuth {
                    throw IllegalStateException("Test exception")
                }
                null
            } catch (e: IllegalStateException) {
                e
            }

            assertNotNull(exception)
            assertEquals("Test exception", exception.message)
        }
    }

    test("withServiceAuth can be nested with different user contexts") {
        runTest {
            val provider = object : ServiceTokenProvider {
                override suspend fun getToken(): ServiceToken {
                    return ServiceToken(
                        accessToken = "service-token",
                        expiresAt = Clock.System.now() + 5.minutes
                    )
                }
            }

            provider.withServiceAuth(userToken = "user-outer") {
                val outerContext = currentCoroutineContext()[AuthContext]
                assertIs<AuthContext.ServiceAuth>(outerContext)
                assertEquals("user-outer", outerContext.userToken)

                provider.withServiceAuth(userToken = "user-inner") {
                    val innerContext = currentCoroutineContext()[AuthContext]
                    assertIs<AuthContext.ServiceAuth>(innerContext)
                    assertEquals("user-inner", innerContext.userToken)
                }

                val restoredContext = currentCoroutineContext()[AuthContext]
                assertIs<AuthContext.ServiceAuth>(restoredContext)
                assertEquals("user-outer", restoredContext.userToken)
            }
        }
    }
}

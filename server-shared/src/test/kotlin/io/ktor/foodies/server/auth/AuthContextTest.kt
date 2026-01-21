package io.ktor.foodies.server.auth

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

val authContextSpec by testSuite {
    test("UserAuth coroutine context propagation") {
        runTest {
            val userAuth = AuthContext.UserAuth("user-token-123")

            withContext(userAuth) {
                val context = currentCoroutineContext()[AuthContext]
                assertNotNull(context)
                assertIs<AuthContext.UserAuth>(context)
                assertEquals("user-token-123", context.accessToken)
            }
        }
    }

    test("ServiceAuth coroutine context propagation without user token") {
        runTest {
            val serviceAuth = AuthContext.ServiceAuth("service-token-456")

            withContext(serviceAuth) {
                val context = currentCoroutineContext()[AuthContext]
                assertNotNull(context)
                assertIs<AuthContext.ServiceAuth>(context)
                assertEquals("service-token-456", context.serviceToken)
                assertNull(context.userToken)
            }
        }
    }

    test("ServiceAuth coroutine context propagation with user token") {
        runTest {
            val serviceAuth = AuthContext.ServiceAuth(
                serviceToken = "service-token-789",
                userToken = "user-token-original"
            )

            withContext(serviceAuth) {
                val context = currentCoroutineContext()[AuthContext]
                assertNotNull(context)
                assertIs<AuthContext.ServiceAuth>(context)
                assertEquals("service-token-789", context.serviceToken)
                assertEquals("user-token-original", context.userToken)
            }
        }
    }

    test("AuthContext propagates to nested coroutines") {
        runTest {
            val userAuth = AuthContext.UserAuth("nested-token")

            withContext(userAuth) {
                val deferred = async {
                    currentCoroutineContext()[AuthContext]
                }

                val context = deferred.await()
                assertNotNull(context)
                assertIs<AuthContext.UserAuth>(context)
                assertEquals("nested-token", context.accessToken)
            }
        }
    }

    test("AuthContext can be replaced in nested context") {
        runTest {
            val userAuth = AuthContext.UserAuth("outer-token")
            val serviceAuth = AuthContext.ServiceAuth("inner-token")

            withContext(userAuth) {
                val outerContext = currentCoroutineContext()[AuthContext]
                assertIs<AuthContext.UserAuth>(outerContext)
                assertEquals("outer-token", outerContext.accessToken)

                withContext(serviceAuth) {
                    val innerContext = currentCoroutineContext()[AuthContext]
                    assertIs<AuthContext.ServiceAuth>(innerContext)
                    assertEquals("inner-token", innerContext.serviceToken)
                }

                val restoredContext = currentCoroutineContext()[AuthContext]
                assertIs<AuthContext.UserAuth>(restoredContext)
                assertEquals("outer-token", restoredContext.accessToken)
            }
        }
    }

    test("AuthContext is absent when not set") {
        runTest {
            val context = currentCoroutineContext()[AuthContext]
            assertNull(context)
        }
    }

    test("UserAuth has correct CoroutineContext.Key") {
        val userAuth = AuthContext.UserAuth("token")
        assertEquals(AuthContext.Key, userAuth.key)
    }

    test("ServiceAuth has correct CoroutineContext.Key") {
        val serviceAuth = AuthContext.ServiceAuth("token")
        assertEquals(AuthContext.Key, serviceAuth.key)
    }

    test("AuthContext.Key can retrieve context element") {
        runTest {
            val userAuth = AuthContext.UserAuth("test-token")

            withContext(userAuth) {
                val context: CoroutineContext = currentCoroutineContext()
                val retrievedAuth = context[AuthContext.Key]

                assertNotNull(retrievedAuth)
                assertIs<AuthContext.UserAuth>(retrievedAuth)
                assertEquals("test-token", retrievedAuth.accessToken)
            }
        }
    }

    test("Multiple async coroutines inherit same AuthContext") {
        runTest {
            val userAuth = AuthContext.UserAuth("shared-token")

            withContext(userAuth) {
                val deferred1 = async {
                    currentCoroutineContext()[AuthContext]
                }

                val deferred2 = async {
                    currentCoroutineContext()[AuthContext]
                }

                val context1 = deferred1.await()
                val context2 = deferred2.await()

                assertNotNull(context1)
                assertNotNull(context2)
                assertIs<AuthContext.UserAuth>(context1)
                assertIs<AuthContext.UserAuth>(context2)
                assertEquals("shared-token", context1.accessToken)
                assertEquals("shared-token", context2.accessToken)
            }
        }
    }

    test("ServiceAuth with null userToken is semantically same as without it") {
        runTest {
            val serviceAuth1 = AuthContext.ServiceAuth("token")
            val serviceAuth2 = AuthContext.ServiceAuth("token", null)

            assertEquals(serviceAuth1, serviceAuth2)
        }
    }
}

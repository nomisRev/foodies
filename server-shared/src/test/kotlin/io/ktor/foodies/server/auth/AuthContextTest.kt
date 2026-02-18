package io.ktor.foodies.server.auth

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

val authContextSpec by testSuite {
    test("UserAuth coroutine context propagation") {
        val userAuth = AuthContext("user-token-123")

        withContext(userAuth) {
            val context = currentCoroutineContext()[AuthContext]
            assertNotNull(context)
            assertIs<AuthContext>(context)
            assertEquals("user-token-123", context.accessToken)
        }
    }

    test("AuthContext propagates to nested coroutines") {
        val userAuth = AuthContext("nested-token")

        withContext(userAuth) {
            val deferred = async {
                currentCoroutineContext()[AuthContext]
            }

            val context = deferred.await()
            assertNotNull(context)
            assertIs<AuthContext>(context)
            assertEquals("nested-token", context.accessToken)
        }
    }


    test("AuthContext is absent when not set") {
        val context = currentCoroutineContext()[AuthContext]
        assertNull(context)
    }

    test("UserAuth has correct CoroutineContext.Key") {
        val userAuth = AuthContext("token")
        assertEquals(AuthContext.Key, userAuth.key)
    }

    test("AuthContext.Key can retrieve context element") {
        val userAuth = AuthContext("test-token")

        withContext(userAuth) {
            val context: CoroutineContext = currentCoroutineContext()
            val retrievedAuth = context[AuthContext.Key]

            assertNotNull(retrievedAuth)
            assertIs<AuthContext>(retrievedAuth)
            assertEquals("test-token", retrievedAuth.accessToken)
        }
    }

    test("Multiple async coroutines inherit same AuthContext") {
        val userAuth = AuthContext("shared-token")

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
            assertIs<AuthContext>(context1)
            assertIs<AuthContext>(context2)
            assertEquals("shared-token", context1.accessToken)
            assertEquals("shared-token", context2.accessToken)
        }
    }
}

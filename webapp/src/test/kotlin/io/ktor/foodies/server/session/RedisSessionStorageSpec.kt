package io.ktor.foodies.server.session

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import io.ktor.foodies.server.test.ctxSuite
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.delay
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalLettuceCoroutinesApi::class)
val redisSessionStorageSpec by ctxSuite(context = { serviceContext() }) {

    testRedis("write and read session") { storage ->
        val sessionValue = "test-session-data"

        storage.write("session-1", sessionValue)
        val retrieved = storage.read("session-1")

        assertEquals(sessionValue, retrieved)
    }

    testRedis("read non-existent session throws exception") { storage ->
        assertFailsWith<NoSuchElementException> {
            storage.read("non-existent")
        }
    }

    testRedis("invalidate removes session") { storage ->
        val sessionValue = "test-session-data-2"

        storage.write("session-2", sessionValue)
        storage.invalidate("session-2")

        assertFailsWith<NoSuchElementException> {
            storage.read("session-2")
        }
    }

    testRedis("sessions are isolated by ID") { storage ->
        val session1 = "token-user-1"
        val session2 = "token-user-2"

        storage.write("user-1", session1)
        storage.write("user-2", session2)

        val retrieved1 = storage.read("user-1")
        val retrieved2 = storage.read("user-2")

        assertEquals("token-user-1", retrieved1)
        assertEquals("token-user-2", retrieved2)
    }

    testRedis("overwriting session updates data") { storage ->
        val session1 = "original-token"
        val session2 = "updated-token"

        storage.write("session-5", session1)
        storage.write("session-5", session2)

        val retrieved = storage.read("session-5")

        assertEquals("updated-token", retrieved)
    }

    testSuite(
        name = "real-time",
        testConfig = TestConfig.testScope(false)
    ) {
        testRedis("session expires after TTL", ttlSeconds = 1) { storage ->
            val sessionValue = "test-session-data-3"

            storage.write("session-3", sessionValue)

            assertEquals(sessionValue, storage.read("session-3"))

            delay(2000)

            assertFailsWith<NoSuchElementException> {
                storage.read("session-3")
            }
        }

        testRedis("reading session does not refresh TTL", ttlSeconds = 2) { storage ->
            val sessionValue = "test-session-data-refresh"

            storage.write("session-4", sessionValue)

            delay(1000)
            assertEquals(sessionValue, storage.read("session-4"))

            delay(1500)

            // Session should be expired because read() didn't refresh it
            assertFailsWith<NoSuchElementException> {
                storage.read("session-4")
            }
        }
    }
}

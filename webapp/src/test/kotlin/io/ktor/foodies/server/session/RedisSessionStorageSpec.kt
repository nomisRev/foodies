package io.ktor.foodies.server.session

import com.redis.testcontainers.RedisContainer
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

@Testcontainers
@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisSessionStorageSpec {

    companion object {
        @Container
        @JvmStatic
        val redis = RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.withTag("7-alpine"))

        lateinit var redisClient: RedisClient

        @BeforeAll
        @JvmStatic
        fun setup() {
            redisClient = RedisClient.create(redis.redisURI)
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            if (::redisClient.isInitialized) {
                redisClient.shutdown()
            }
        }
    }

    @Test
    fun `write and read session`() = runBlocking {
        val storage = RedisSessionStorage(redisClient.connect().coroutines())
        val sessionValue = "test-session-data"

        storage.write("session-1", sessionValue)
        val retrieved = storage.read("session-1")

        assertEquals(sessionValue, retrieved)
    }

    @Test
    fun `read non-existent session throws exception`() = runBlocking {
        val storage = RedisSessionStorage(redisClient.connect().coroutines())

        org.junit.jupiter.api.assertThrows<NoSuchElementException> {
            runBlocking {
                storage.read("non-existent")
            }
        }
    }

    @Test
    fun `invalidate removes session`() = runBlocking {
        val storage = RedisSessionStorage(redisClient.connect().coroutines())
        val sessionValue = "test-session-data-2"

        storage.write("session-2", sessionValue)
        storage.invalidate("session-2")
        
        org.junit.jupiter.api.assertThrows<NoSuchElementException> {
            runBlocking {
                storage.read("session-2")
            }
        }
    }

    @Test
    fun `session expires after TTL`() = runBlocking {
        val storage = RedisSessionStorage(
            redis = redisClient.connect().coroutines(),
            ttlSeconds = 1
        )
        val sessionValue = "test-session-data-3"

        storage.write("session-3", sessionValue)

        // Verify session exists
        assertEquals(sessionValue, storage.read("session-3"))

        // Wait for expiration
        delay(2.seconds)

        // Session should be expired
        org.junit.jupiter.api.assertThrows<NoSuchElementException> {
            runBlocking {
                storage.read("session-3")
            }
        }
    }

    @Test
    fun `reading session does not refresh TTL`() = runBlocking {
        // RedisSessionStorage implementation of read doesn't refresh TTL (it just calls get)
        val storage = RedisSessionStorage(
            redis = redisClient.connect().coroutines(),
            ttlSeconds = 2
        )
        val sessionValue = "test-session-data-refresh"

        storage.write("session-4", sessionValue)

        // Read after 1 second (before expiration)
        delay(1.seconds)
        assertEquals(sessionValue, storage.read("session-4"))

        // Wait another 1.5 seconds (total 2.5)
        delay(1.5.seconds)

        // Session should be expired because read() didn't refresh it
        org.junit.jupiter.api.assertThrows<NoSuchElementException> {
            runBlocking {
                storage.read("session-4")
            }
        }
    }

    @Test
    fun `sessions are isolated by ID`() = runBlocking {
        val storage = RedisSessionStorage(redisClient.connect().coroutines())
        val session1 = "token-user-1"
        val session2 = "token-user-2"

        storage.write("user-1", session1)
        storage.write("user-2", session2)

        val retrieved1 = storage.read("user-1")
        val retrieved2 = storage.read("user-2")

        assertEquals("token-user-1", retrieved1)
        assertEquals("token-user-2", retrieved2)
    }

    @Test
    fun `overwriting session updates data`() = runBlocking {
        val storage = RedisSessionStorage(redisClient.connect().coroutines())
        val session1 = "original-token"
        val session2 = "updated-token"

        storage.write("session-5", session1)
        storage.write("session-5", session2)

        val retrieved = storage.read("session-5")

        assertEquals("updated-token", retrieved)
    }
}

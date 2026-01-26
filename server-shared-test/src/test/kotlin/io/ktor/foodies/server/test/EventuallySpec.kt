package io.ktor.foodies.server.test

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.delay
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

val eventuallySpec by testSuite {
    test("eventually succeeds when block passes immediately") {
        var counter = 0
        eventually {
            counter++
            assertEquals(1, counter)
        }
        assertEquals(1, counter)
    }

    test("eventually succeeds when block passes after retries") {
        var counter = 0
        eventually(timeout = 1.seconds) {
            counter++
            if (counter < 3) throw AssertionError("Not yet")
        }
        assertEquals(3, counter)
    }

    test("eventually throws last exception when timeout is exceeded") {
        var counter = 0
        val exception = assertFailsWith<AssertionError> {
            eventually(timeout = 100.milliseconds) {
                counter++
                throw AssertionError("Always fails")
            }
        }
        assertEquals("Always fails", exception.message)
    }

    test("eventually throws timeout error when no exception is caught") {
        val exception = assertFailsWith<AssertionError> {
            eventually(timeout = 100.milliseconds) {
                delay(200.milliseconds)
            }
        }
        assertEquals("Timeout exceeded", exception.message)
    }

    test("eventually respects custom timeout") {
        var counter = 0
        val startTime = kotlin.time.TimeSource.Monotonic.markNow()
        assertFailsWith<AssertionError> {
            eventually(timeout = 200.milliseconds) {
                counter++
                throw AssertionError("Fail")
            }
        }
        val elapsed = startTime.elapsedNow()
        assert(elapsed >= 200.milliseconds) { "Should wait at least timeout duration" }
    }
}

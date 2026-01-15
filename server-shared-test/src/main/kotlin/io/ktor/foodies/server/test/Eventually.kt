package io.ktor.foodies.server.test

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

inline fun eventually(timeout: Duration = 3.seconds, block: () -> Unit) {
    var last: Throwable? = null
    val now = TimeSource.Monotonic.markNow()
    while (now.elapsedNow() < timeout) {
        try {
            block()
            return
        } catch (e: Throwable) {
            last = e
        }
    }
    if (last != null) throw last else throw AssertionError("Timeout exceeded")
}
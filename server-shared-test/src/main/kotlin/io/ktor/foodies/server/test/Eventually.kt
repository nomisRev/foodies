package io.ktor.foodies.server.test

import kotlin.contracts.ExperimentalContracts
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalContracts::class)
suspend fun eventually(timeout: Duration = 3.seconds, block: suspend CoroutineScope.() -> Unit) {
    var last: Throwable? = null
    val startTime = TimeSource.Monotonic.markNow()
    while (startTime.elapsedNow() < timeout) {
        val remaining = timeout - startTime.elapsedNow()
        if (remaining <= Duration.ZERO) break
        try {
            return withTimeoutOrNull(remaining) { block() }
                ?: throw AssertionError("Timeout exceeded")
        } catch (e: Throwable) {
            last = e
        }
    }
    if (last != null) throw last else throw AssertionError("Timeout exceeded")
}

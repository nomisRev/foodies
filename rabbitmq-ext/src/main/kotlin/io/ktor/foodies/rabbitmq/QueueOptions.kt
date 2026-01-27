package io.ktor.foodies.rabbitmq

import kotlin.time.Duration

sealed interface RetryPolicy {
    data object None : RetryPolicy
    data class MaxAttempts(val value: Int) : RetryPolicy
}

sealed interface DeadLetterPolicy {
    data class Enabled(
        val exchange: (original: String) -> String = { "$it.dlx" },
        val routingKey: (original: String) -> String = { "$it.dlq" }
    ) : DeadLetterPolicy

    data object Disabled : DeadLetterPolicy
}

class QueueOptionsBuilder<A> {
    var durable: Boolean = true
    var ttl: Duration? = null
    var retry: RetryPolicy = RetryPolicy.MaxAttempts(5)
    var deadLetter: DeadLetterPolicy = DeadLetterPolicy.Enabled()
}

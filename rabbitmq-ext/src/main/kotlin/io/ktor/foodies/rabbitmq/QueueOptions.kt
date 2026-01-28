package io.ktor.foodies.rabbitmq

import kotlin.time.Duration

sealed interface RetryPolicy {
    data object None : RetryPolicy
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
    val exclusive: Boolean = false
    val autoDelete: Boolean = false
    var ttl: Duration? = null
    var deadLetter: DeadLetterPolicy = DeadLetterPolicy.Enabled()
}

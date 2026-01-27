package io.ktor.foodies.rabbitmq

import com.rabbitmq.client.Channel
import com.rabbitmq.client.Delivery

/**
 * Wrapper around a RabbitMQ message providing the deserialized value
 * and manual acknowledgment controls.
 *
 * @param A The type of the deserialized message payload
 * @property value The deserialized message payload
 */
class Message<A>(val value: A, private val delivery: Delivery, private val channel: Channel) {
    val deliveryAttempts: Int by lazy {
        val xDeath = delivery.properties.headers?.get("x-death") as? List<*>
        xDeath?.firstOrNull()?.let { entry ->
            (entry as? Map<*, *>)?.get("count")?.let { count ->
                when (count) {
                    is Long -> count.toInt()
                    is Int -> count
                    else -> 0
                }
            }
        } ?: 0
    }

    /**
     * Acknowledges the message, removing it from the queue.
     */
    fun ack(): Unit = channel.basicAck(delivery.envelope.deliveryTag, false)

    /**
     * Negatively acknowledges the message without requeuing.
     * The message will be discarded or sent to a dead-letter queue if configured.
     */
    fun nack(): Unit = channel.basicNack(delivery.envelope.deliveryTag, false, false)
}

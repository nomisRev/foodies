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
class Message<A>(
    val value: A,
    private val delivery: Delivery,
    private val channel: Channel
) {
    /**
     * Acknowledges the message, removing it from the queue.
     */
    fun ack(): Unit = channel.basicAck(delivery.envelope.deliveryTag, false)

    /**
     * Negatively acknowledges the message.
     */
    fun nack(): Unit = channel.basicNack(delivery.envelope.deliveryTag, false, false)
}

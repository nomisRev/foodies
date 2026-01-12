package io.ktor.foodies.rabbitmq

import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Channel
import com.rabbitmq.client.DeliverCallback
import com.rabbitmq.client.Delivery
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(Consumer::class.java)

/**
 * Functional interface for message consumers.
 * Implementations process messages from a RabbitMQ queue as a Flow.
 */
fun interface Consumer {
    fun process(): Flow<Unit>
}

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
     * Negatively acknowledges the message without requeuing.
     * The message will be discarded or sent to a dead-letter queue if configured.
     */
    fun nack(): Unit = channel.basicNack(delivery.envelope.deliveryTag, false, false)
}

/**
 * Creates a Flow of messages from this channel for the specified queue.
 * Messages are deserialized using kotlinx.serialization.
 *
 * The flow uses manual acknowledgment (autoAck=false). Consumers must call
 * [Message.ack] or [Message.nack] for each message.
 *
 * Backpressure is applied to RabbitMQ through blocking on the delivery callback.
 *
 * @param A The type to deserialize messages into
 * @param queueName The name of the queue to consume from
 * @return A Flow of deserialized messages
 */
inline fun <reified A> Channel.messages(queueName: String): Flow<Message<A>> =
    messages(serializer(), queueName)

/**
 * Creates a Flow of messages using an explicit serializer.
 *
 * @param A The type to deserialize messages into
 * @param serializer The kotlinx.serialization serializer to use
 * @param queueName The name of the queue to consume from
 * @return A Flow of deserialized messages
 */
fun <A> Channel.messages(serializer: KSerializer<A>, queueName: String): Flow<Message<A>> =
    channelFlow {
        val deliverCallback = DeliverCallback { _, delivery ->
            runCatching {
                Json.decodeFromString(serializer, delivery.body.decodeToString())
            }.fold(
                /**
                 * We use trySendBlocking here because we want to 'backpressure' the DeliveryCallback.
                 * Since it is a Java SDK it expects blocking for backpressure.
                 */
                { payload -> trySendBlocking(Message(payload, delivery, this@messages)) },
                { error -> close(error) }
            )
        }
        val cancelCallback = CancelCallback {
            logger.warn("Consumer cancelled for queue $queueName")
            channel.close()
        }
        val consumerTag = basicConsume(queueName, false, deliverCallback, cancelCallback)
        awaitClose { basicCancel(consumerTag) }
    }

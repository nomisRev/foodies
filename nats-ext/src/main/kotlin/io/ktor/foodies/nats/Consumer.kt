package io.ktor.foodies.nats

import io.nats.client.JetStream
import io.nats.client.Message
import io.nats.client.PullSubscribeOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory
import java.time.Duration

private val logger = LoggerFactory.getLogger("io.ktor.foodies.nats.Consumer")

/**
 * Functional interface for message consumers.
 * Implementations process messages from a NATS JetStream as a Flow.
 */
fun interface Consumer {
    fun process(): Flow<Unit>
}

/**
 * Wrapper around a NATS message providing the deserialized value
 * and manual acknowledgment controls.
 *
 * @param A The type of the deserialized message payload
 * @property value The deserialized message payload
 */
class JetStreamMessage<A>(
    val value: A,
    private val natsMessage: Message
) {
    /**
     * Acknowledges the message, removing it from the stream/consumer.
     */
    fun ack(): Unit = natsMessage.ack()

    /**
     * Negatively acknowledges the message.
     */
    fun nack(): Unit = natsMessage.nak()
}

/**
 * Creates a Flow of messages from JetStream for the specified subject and stream.
 *
 * @param A The type to deserialize messages into
 * @param stream The name of the stream
 * @param subject The subject to subscribe to
 * @param durable The durable name for the consumer
 * @return A Flow of deserialized messages
 */
inline fun <reified A> JetStream.messages(
    stream: String,
    subject: String,
    durable: String
): Flow<JetStreamMessage<A>> =
    messages(serializer(), stream, subject, durable)

/**
 * Creates a Flow of messages using an explicit serializer.
 */
fun <A> JetStream.messages(
    serializer: KSerializer<A>,
    stream: String,
    subject: String,
    durable: String
): Flow<JetStreamMessage<A>> = channelFlow {
    val options = PullSubscribeOptions.builder()
        .stream(stream)
        .durable(durable)
        .build()

    val subscription = subscribe(subject, options)

    try {
        while (!isClosedForSend) {
            val messages = subscription.fetch(10, Duration.ofSeconds(1))
            for (msg in messages) {
                runCatching {
                    Json.decodeFromString(serializer, msg.data.decodeToString())
                }.fold(
                    { payload -> send(JetStreamMessage(payload, msg)) },
                    { error ->
                        logger.error("Failed to deserialize message", error)
                        close(error)
                    }
                )
            }
        }
    } finally {
        subscription.unsubscribe()
    }
}

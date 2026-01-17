package io.ktor.foodies.rabbitmq

import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DeliverCallback
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory
import java.io.IOException

private val logger = LoggerFactory.getLogger("io.ktor.foodies.rabbitmq.RabbitMQ")

/**
 * Configuration for RabbitMQ connection.
 */
data class RabbitConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String
)

fun rabbitConnectionFactory(
    host: String,
    port: Int,
    username: String,
    password: String
): ConnectionFactory = ConnectionFactory().apply {
    this.host = host
    this.port = port
    this.username = username
    this.password = password
}

fun RabbitMQSubscriber(connection: Connection, exchange: String): RabbitMQSubscriber = RabbitMQ(connection, exchange)

interface RabbitMQSubscriber {
    /**
     * Creates a Flow of messages using an explicit serializer.
     *
     * Parallel (unordered) processing example:
     * ```kotlin
     * channel.messages(serializer<MyEvent>(), "my-queue")
     *     .buffer(1000) // default is 64
     *     .flatMapMerge(concurrency = 16 /* default */) { event: Message<MyEvent> ->
     *         flowOf {
     *            try {
     *                process(event.value)
     *                event.ack()
     *            } catch(e: Exception) {
     *                event.nack()
     *            }
     *         }
     *     }.collect()
     * ```
     *
     * @param A The type to deserialize messages into
     * @param serializer The serializer to use
     * @param queueName The name of the queue to consume from
     * @return A Flow of deserialized messages
     */
    fun <A> subscribe(
        serializer: KSerializer<A>,
        queueName: String,
        configure: Channel.(exchange: String) -> Unit = { exchange ->
            queueDeclare(queueName, true, false, false, null)
        }
    ): Flow<Message<A>>
}

inline fun <reified A> RabbitMQSubscriber.subscribe(
    queueName: String,
    noinline configure: Channel.(exchange: String) -> Unit = { exchange ->
        queueDeclare(queueName, true, false, false, null)
    }
): Flow<Message<A>> = subscribe(serializer<A>(), queueName, configure)

fun <A, B> Flow<Message<A>>.consumeMessage(block: suspend (message: A) -> B): Flow<B> =
    map { message ->
        runCatching {
            block(message.value).also { message.ack() }
        }.onFailure {
            logger.error("Error while processing message: ${message.value}", it)
            message.nack()
        }.getOrThrow()
    }

private class RabbitMQ(private val connection: Connection, private val exchange: String) : RabbitMQSubscriber {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun <A> subscribe(
        serializer: KSerializer<A>,
        queueName: String,
        configure: Channel.(exchange: String) -> Unit
    ): Flow<Message<A>> = channelFlow {
        val channel = connection.createChannel().apply { configure(exchange) }
        val deliverCallback = DeliverCallback { _, delivery ->
            runCatching {
                Json.decodeFromString(serializer, delivery.body.decodeToString())
            }.fold(
                /**
                 * We use trySendBlocking here because we want to 'backpressure' the DeliveryCallback.
                 * Since it is a Java SDK it expects blocking for backpressure.
                 */
                { payload -> trySendBlocking(Message(payload, delivery, channel)) },
                { error ->
                    channel.basicNack(delivery.envelope.deliveryTag, false, true)
                    close(error)
                }
            )
        }
        val cancelCallback = CancelCallback {
            logger.warn("Consumer cancelled for queue $queueName")
            channel.close()
        }
        val consumerTag = try {
            channel.basicConsume(queueName, false, deliverCallback, cancelCallback)
        } catch (e: IOException) {
            logger.debug("Failed to consume messages from queue $queueName", e)
            channel.close()
            throw e
        }
        awaitClose { channel.basicCancel(consumerTag) }
    }
}

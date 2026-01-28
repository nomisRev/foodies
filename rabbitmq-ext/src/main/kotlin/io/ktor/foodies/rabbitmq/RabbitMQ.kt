package io.ktor.foodies.rabbitmq

import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DeliverCallback
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.DEFAULT_CONCURRENCY
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException

private val logger = LoggerFactory.getLogger("io.ktor.foodies.rabbitmq.RabbitMQ")


fun RabbitMQSubscriber(connection: Connection, exchange: String): RabbitMQSubscriber = RabbitMQ(connection, exchange)

interface RabbitMQSubscriber {
    /**
     * Creates a Flow of messages using an explicit serializer.
     *
     * Example:
     * ```kotlin
     * subscriber.subscribe<MyEvent>("my-queue")
     *     .parConsumeMessage { event ->
     *         process(event)git
     *     }.collect()
     * ```
     *
     * @param A The type to deserialize messages into
     * @param serializer The serializer to use
     * @param queueName The name of the queue to consume from
     * @param retryPolicy The retry policy to apply for this queue
     * @return A Flow of deserialized messages
     */
    fun <A> subscribe(
        serializer: KSerializer<A>,
        queueName: String,
        retryPolicy: RetryPolicy = RetryPolicy.None,
        configure: Channel.(exchange: String) -> Unit = { exchange ->
            queueDeclare(queueName, true, false, false, null)
        }
    ): Flow<Message<A>>
}

fun <A> RabbitMQSubscriber.subscribe(
    routingKey: RoutingKey<A>,
    queueName: String,
    configure: Channel.(exchange: String) -> Unit = { exchange ->
        queueDeclare(queueName, true, false, false, null)
        queueBind(queueName, exchange, routingKey.key)
    }
): Flow<Message<A>> = subscribe(routingKey.serializer, queueName, RetryPolicy.None, configure)

fun <A> RabbitMQSubscriber.subscribe(
    queueName: String,
    routingKey: RoutingKey<A>,
    configure: QueueOptionsBuilder<A>.() -> Unit = {}
): Flow<Message<A>> {
    val options = QueueOptionsBuilder<A>().apply(configure)
    return subscribe(routingKey.serializer, queueName, options.retry) { exchange ->
        when (val dl = options.deadLetter) {
            is DeadLetterPolicy.Enabled -> {
                val dlxName = dl.exchange(exchange)
                val dlqName = dl.routingKey(queueName)
                exchangeDeclare(dlxName, "topic", options.durable)
                queueDeclare(dlqName, options.durable, false, false, null)
                queueBind(dlqName, dlxName, dlqName)
            }
            DeadLetterPolicy.Disabled -> {}
        }
        val args = buildMap<String, Any> {
            when (val dl = options.deadLetter) {
                is DeadLetterPolicy.Enabled -> {
                    put("x-dead-letter-exchange", dl.exchange(exchange))
                    put("x-dead-letter-routing-key", dl.routingKey(queueName))
                }
                DeadLetterPolicy.Disabled -> {}
            }
            options.ttl?.let { put("x-message-ttl", it.inWholeMilliseconds) }
        }
        queueDeclare(queueName, options.durable, false, false, args.ifEmpty { null })
        queueBind(queueName, exchange, routingKey.key)
    }
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
fun <A, B> Flow<Message<A>>.parConsumeMessage(
    concurrency: Int = DEFAULT_CONCURRENCY,
    block: suspend (message: A) -> B
): Flow<B> = flatMapMerge(concurrency) { message ->
    suspend {
        runCatching {
            block(message.value).also { message.ack() }
        }.onFailure {
            logger.error("Error while processing message: ${message.value}", it)
            message.nack()
        }.getOrThrow()
    }.asFlow()
}

private class RabbitMQ(private val connection: Connection, private val exchange: String) : RabbitMQSubscriber {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun <A> subscribe(
        serializer: KSerializer<A>,
        queueName: String,
        retryPolicy: RetryPolicy,
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
                { payload -> trySendBlocking(Message(payload, delivery, channel, retryPolicy)) },
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

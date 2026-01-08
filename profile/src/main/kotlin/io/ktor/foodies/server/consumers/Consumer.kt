package io.ktor.foodies.server.consumers

import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DeliverCallback
import com.rabbitmq.client.Delivery
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(Consumer::class.java)

fun interface Consumer {
    fun process(): Flow<Unit>
}

private const val CONSUMER_CONNECTION_NAME = "profile-service"

context(application: Application)
fun ConnectionFactory.channel(queueName: String): Channel {
    val connection = newConnection(CONSUMER_CONNECTION_NAME)
    val channel = connection.createChannel().apply {
        queueDeclare(queueName, true, false, false, null)
    }
    application.monitor.subscribe(ApplicationStopped) {
        runCatching { channel.close() }.onFailure { logger.warn("Failed to close RabbitMQ channel", it) }
        runCatching { connection.close() }.onFailure { logger.warn("Failed to close RabbitMQ connection", it) }
    }
    return channel
}

class Message<A>(val value: A, private val delivery: Delivery, private val channel: Channel) {
    fun ack() = channel.basicAck(delivery.envelope.deliveryTag, false)
    fun nack() = channel.basicNack(delivery.envelope.deliveryTag, false, false)
}

inline fun <reified A> Channel.messages(
    queueName: String,
    json: Json = Json,
): Flow<Message<A>> = messages(json, serializer(), queueName)

fun <A> Channel.messages(
    json: Json,
    serializer: KSerializer<A>,
    queueName: String
): Flow<Message<A>> = channelFlow {
    val deliverCallback = DeliverCallback { _, delivery ->
        runCatching {
            json.decodeFromString(serializer, delivery.body.decodeToString())
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
        logger.warn("Registration consumer cancelled for queue $queueName")
        channel.close()
    }
    val consumerTag = basicConsume(queueName, false, deliverCallback, cancelCallback)
    awaitClose { basicCancel(consumerTag) }
}

package io.ktor.foodies.rabbitmq

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import kotlinx.serialization.serializer

interface HasRoutingKey {
    val key: String
}

interface Publisher {
    fun <A : HasRoutingKey> publish(
        serializer: KSerializer<A>,
        message: A,
        props: AMQP.BasicProperties? = null,
    )
}

inline fun <reified A : HasRoutingKey> Publisher.publish(
    message: A,
    props: AMQP.BasicProperties? = null,
) = publish(serializer<A>(), message, props)

fun Publisher(channel: Channel, exchange: String, format: StringFormat): Publisher =
    PublisherImpl(channel, exchange, format)

private class PublisherImpl(
    private val channel: Channel,
    private val exchange: String,
    private val format: StringFormat,
) : Publisher {
    override fun <A : HasRoutingKey> publish(
        serializer: KSerializer<A>,
        message: A,
        props: AMQP.BasicProperties?,
    ) {
        val json = format.encodeToString(serializer, message)
        channel.basicPublish(exchange, message.key, props, json.toByteArray())
    }
}

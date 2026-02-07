package io.ktor.foodies.rabbitmq

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import kotlinx.serialization.Transient

data class RoutingKey<A>(val key: String, val serializer: KSerializer<A>)

interface HasRoutingKey<A> {
    @Transient val routingKey: RoutingKey<A>
}

interface Publisher {
    fun <A : HasRoutingKey<A>> publish(message: A, props: AMQP.BasicProperties? = null)
}

fun Publisher(channel: Channel, exchange: String, format: StringFormat): Publisher =
    PublisherImpl(channel, exchange, format)

private class PublisherImpl(
    private val channel: Channel,
    private val exchange: String,
    private val format: StringFormat,
) : Publisher {
    override fun <A : HasRoutingKey<A>> publish(message: A, props: AMQP.BasicProperties?) {
        val json = format.encodeToString(message.routingKey.serializer, message)
        channel.basicPublish(exchange, message.routingKey.key, props, json.toByteArray())
    }
}

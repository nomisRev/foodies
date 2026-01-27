package io.ktor.foodies.rabbitmq

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import kotlinx.serialization.serializer

data class RoutingKey<A>(val key: String, val serializer: KSerializer<A>)

interface RoutingKeyOwner<A> {
    val routingKey: RoutingKey<A>
}

@Deprecated("Replace with RoutingKeyOwner variant")
interface HasRoutingKey {
    val key: String
}

interface Publisher {
    fun <A : HasRoutingKey> publish(
        serializer: KSerializer<A>,
        message: A,
        props: AMQP.BasicProperties? = null,
    )

    fun <A : RoutingKeyOwner<A>> publish(message: A, props: AMQP.BasicProperties? = null)
}

@Deprecated("Replace with RoutingKeyOwner variant")
inline fun <reified A : HasRoutingKey> Publisher.publish(message: A, props: AMQP.BasicProperties? = null) =
    publish(serializer<A>(), message, props)

fun Publisher(channel: Channel, exchange: String, format: StringFormat): Publisher =
    PublisherImpl(channel, exchange, format)

private class PublisherImpl(
    private val channel: Channel,
    private val exchange: String,
    private val format: StringFormat
) : Publisher {
    @Deprecated("Replace with RoutingKeyOwner variant")
    override fun <A : HasRoutingKey> publish(
        serializer: KSerializer<A>,
        message: A,
        props: AMQP.BasicProperties?
    ) {
        val json = format.encodeToString(serializer, message)
        channel.basicPublish(exchange, message.key, props, json.toByteArray())
    }

    override fun <A : RoutingKeyOwner<A>> publish(message: A, props: AMQP.BasicProperties?) {
        val json = format.encodeToString(message.routingKey.serializer, message)
        channel.basicPublish(exchange, message.routingKey.key, props, json.toByteArray())
    }
}

package io.ktor.foodies.rabbitmq

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.rabbitmq.RoutingKey
import io.ktor.foodies.rabbitmq.RoutingKeyOwner
import io.ktor.foodies.server.test.channel
import io.ktor.foodies.server.test.rabbitContainer
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.test.assertEquals

@Serializable
data class TestEvent(val id: String) : RoutingKeyOwner<TestEvent> {
    @Transient
    override val routingKey: RoutingKey<TestEvent> = key()

    companion object {
        fun key(): RoutingKey<TestEvent> = RoutingKey("test.key", serializer())
    }
}

val publisherSpec by testSuite {
    val rabbit = testFixture { rabbitContainer()().connectionFactory() }

    test("publish - successfully publishes message to RabbitMQ") {
        val exchangeName = "publisher.test.exchange"
        val queueName = "publisher.test.queue"
        val routingKey = "test.key"
        val event = TestEvent(id = "event-1")

        rabbit().channel { channel ->
            channel.exchangeDeclare(exchangeName, "topic", true)
            channel.queueDeclare(queueName, true, false, false, null)
            channel.queueBind(queueName, exchangeName, routingKey)
        }

        rabbit().newConnection().use { connection ->
            connection.createChannel().use { channel ->
                val publisher = Publisher(channel, exchangeName, Json)
                publisher.publish(event)
            }
        }

        rabbit().newConnection().use { connection ->
            val message = RabbitMQSubscriber(connection, exchangeName).subscribe(TestEvent.key(), queueName).first()
            assertEquals("event-1", message.value.id)
            assertEquals(routingKey, message.value.routingKey.key)
            message.ack()
        }
    }
}

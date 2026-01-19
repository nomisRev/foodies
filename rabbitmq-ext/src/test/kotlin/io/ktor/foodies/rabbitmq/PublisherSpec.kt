package io.ktor.foodies.rabbitmq

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.server.test.channel
import io.ktor.foodies.server.test.rabbitContainer
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class TestEvent(val id: String, override val key: String) : HasRoutingKey

val publisherSpec by testSuite {
    val rabbit = testFixture { rabbitContainer()().connectionFactory() }

    test("publish - successfully publishes message to RabbitMQ") {
        val exchangeName = "publisher.test.exchange"
        val queueName = "publisher.test.queue"
        val routingKey = "test.key"
        val event = TestEvent(id = "event-1", key = routingKey)

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
            val message =
                RabbitMQSubscriber(connection, exchangeName).subscribe<TestEvent>(queueName).first()
            assertEquals("event-1", message.value.id)
            assertEquals(routingKey, message.value.key)
            message.ack()
        }
    }
}

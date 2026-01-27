package io.ktor.foodies.menu.events

import io.ktor.foodies.events.menu.RejectedItem
import io.ktor.foodies.events.menu.StockConfirmedEvent
import io.ktor.foodies.events.menu.StockRejectedEvent
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.rabbitmq.Publisher
import io.ktor.foodies.rabbitmq.RabbitMQSubscriber
import io.ktor.foodies.rabbitmq.subscribe
import io.ktor.foodies.server.test.channel
import io.ktor.foodies.server.test.rabbitContainer
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.time.Instant

val menuEventPublisherSpec by testSuite {
    val rabbit = testFixture { rabbitContainer()().connectionFactory() }

    test("publish StockConfirmedEvent - successfully publishes message to RabbitMQ") {
        val exchangeName = "menu.test.exchange"
        val queueName = "menu.test.queue.confirmed"
        val event = StockConfirmedEvent(orderId = 1L, confirmedAt = Instant.parse("2026-01-17T23:54:00Z"))

        rabbit().channel { channel ->
            channel.exchangeDeclare(exchangeName, "topic", true)
            channel.queueDeclare(queueName, true, false, false, null)
            channel.queueBind(queueName, exchangeName, event.routingKey.key)
        }

        rabbit().newConnection().use { connection ->
            connection.createChannel().use { channel ->
                val publisher = Publisher(channel, exchangeName, Json)
                val menuEventPublisher = RabbitMenuEventPublisher(publisher)
                menuEventPublisher.publish(event)
            }
        }

        rabbit().newConnection().use { connection ->
            val message =
                RabbitMQSubscriber(connection, exchangeName).subscribe(StockConfirmedEvent.key(), queueName).first()
            assertEquals(1L, message.value.orderId)
            assertEquals("stock.confirmed", event.routingKey.key)
            message.ack()
        }
    }

    test("publish StockRejectedEvent - successfully publishes message to RabbitMQ") {
        val exchangeName = "menu.test.exchange"
        val queueName = "menu.test.queue.rejected"
        val event = StockRejectedEvent(
            orderId = 1L,
            rejectedItems = listOf(RejectedItem(1L, "Pizza", 5, 2)),
            rejectedAt = Instant.parse("2026-01-17T23:54:00Z")
        )

        rabbit().channel { channel ->
            channel.exchangeDeclare(exchangeName, "topic", true)
            channel.queueDeclare(queueName, true, false, false, null)
            channel.queueBind(queueName, exchangeName, event.routingKey.key)
        }

        rabbit().newConnection().use { connection ->
            connection.createChannel().use { channel ->
                val publisher = Publisher(channel, exchangeName, Json)
                val menuEventPublisher = RabbitMenuEventPublisher(publisher)
                menuEventPublisher.publish(event)
            }
        }

        rabbit().newConnection().use { connection ->
            val message =
                RabbitMQSubscriber(connection, exchangeName).subscribe(StockRejectedEvent.key(), queueName).first()
            assertEquals(1L, message.value.orderId)
            assertEquals(1, message.value.rejectedItems.size)
            assertEquals("Pizza", message.value.rejectedItems[0].menuItemName)
            assertEquals("stock.rejected", event.routingKey.key)
            message.ack()
        }
    }
}

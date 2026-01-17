package io.ktor.foodies.order.events

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.order.domain.OrderCancelledEvent
import io.ktor.foodies.order.domain.OrderCreatedEvent
import io.ktor.foodies.order.domain.OrderItemSnapshot
import io.ktor.foodies.order.service.RabbitOrderEventPublisher
import io.ktor.foodies.rabbitmq.Publisher
import io.ktor.foodies.rabbitmq.RabbitMQSubscriber
import io.ktor.foodies.rabbitmq.subscribe
import io.ktor.foodies.server.SerializableBigDecimal
import io.ktor.foodies.server.test.channel
import io.ktor.foodies.server.test.rabbitContainer
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.time.Instant

val orderEventPublisherSpec by testSuite {
    val rabbit = testFixture { rabbitContainer()().connectionFactory() }

    test("publish OrderCreatedEvent - successfully publishes message to RabbitMQ") {
        val exchangeName = "order.test.exchange"
        val queueName = "order.test.queue.created"
        val event = OrderCreatedEvent(
            orderId = 1L,
            buyerId = "user-1",
            items = listOf(OrderItemSnapshot(1L, 2, BigDecimal.TEN)),
            totalPrice = BigDecimal("20.00"),
            currency = "USD",
            createdAt = Instant.parse("2026-01-17T23:54:00Z")
        )

        rabbit().channel { channel ->
            channel.exchangeDeclare(exchangeName, "topic", true)
            channel.queueDeclare(queueName, true, false, false, null)
            channel.queueBind(queueName, exchangeName, event.key)
        }

        rabbit().newConnection().use { connection ->
            connection.createChannel().use { channel ->
                val publisher = Publisher(channel, exchangeName, Json)
                val orderEventPublisher = RabbitOrderEventPublisher(publisher)
                orderEventPublisher.publish(event)
            }
        }

        rabbit().newConnection().use { connection ->
            val message = RabbitMQSubscriber(connection, exchangeName).subscribe<OrderCreatedEvent>(queueName).first()
            assertEquals(1L, message.value.orderId)
            assertEquals("order.created", event.key)
            message.ack()
        }
    }

    test("publish OrderCancelledEvent - successfully publishes message to RabbitMQ") {
        val exchangeName = "order.test.exchange"
        val queueName = "order.test.queue.cancelled"
        val event = OrderCancelledEvent(
            orderId = 1L,
            buyerId = "user-1",
            reason = "Customer changed mind",
            cancelledAt = Instant.parse("2026-01-17T23:55:00Z")
        )

        rabbit().channel { channel ->
            channel.exchangeDeclare(exchangeName, "topic", true)
            channel.queueDeclare(queueName, true, false, false, null)
            channel.queueBind(queueName, exchangeName, event.key)
        }

        rabbit().newConnection().use { connection ->
            connection.createChannel().use { channel ->
                val publisher = Publisher(channel, exchangeName, Json)
                val orderEventPublisher = RabbitOrderEventPublisher(publisher)
                orderEventPublisher.publish(event)
            }
        }

        rabbit().newConnection().use { connection ->
            val message = RabbitMQSubscriber(connection, exchangeName).subscribe<OrderCancelledEvent>(queueName).first()
            assertEquals(1L, message.value.orderId)
            assertEquals("order.cancelled", event.key)
            message.ack()
        }
    }
}

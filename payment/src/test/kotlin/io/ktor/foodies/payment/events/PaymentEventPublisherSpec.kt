package io.ktor.foodies.payment.events

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.events.payment.OrderPaymentSucceededEvent
import io.ktor.foodies.rabbitmq.Publisher
import io.ktor.foodies.rabbitmq.RabbitMQSubscriber
import io.ktor.foodies.rabbitmq.subscribe
import io.ktor.foodies.server.test.channel
import io.ktor.foodies.server.test.rabbitContainer
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

val paymentEventPublisherSpec by testSuite {
    val rabbit = testFixture { rabbitContainer()().connectionFactory() }

    test("publish OrderPaymentSucceededEvent - successfully publishes message to RabbitMQ") {
        val exchangeName = "payment.test.exchange"
        val queueName = "payment.test.queue.succeeded"
        val event =
            OrderPaymentSucceededEvent(
                eventId = "event-123",
                orderId = 1L,
                paymentId = 456L,
                transactionId = "trans-789",
                amount = BigDecimal("100.00"),
                currency = "USD",
                processedAt = Instant.parse("2026-01-17T23:54:00Z"),
            )

        rabbit().channel { channel ->
            channel.exchangeDeclare(exchangeName, "topic", true)
            channel.queueDeclare(queueName, true, false, false, null)
            channel.queueBind(queueName, exchangeName, event.key)
        }

        rabbit().newConnection().use { connection ->
            connection.createChannel().use { channel ->
                val publisher = Publisher(channel, exchangeName, Json)
                val eventPublisher = RabbitMQEventPublisher(publisher)
                eventPublisher.publish(event)
            }
        }

        rabbit().newConnection().use { connection ->
            val message =
                RabbitMQSubscriber(connection, exchangeName)
                    .subscribe<OrderPaymentSucceededEvent>(queueName)
                    .first()
            assertEquals(1L, message.value.orderId)
            assertEquals("payment.succeeded", event.key)
            message.ack()
        }
    }
}

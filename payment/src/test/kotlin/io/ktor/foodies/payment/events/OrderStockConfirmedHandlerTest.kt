package io.ktor.foodies.payment.events

import io.ktor.foodies.events.common.CardBrand
import io.ktor.foodies.events.common.PaymentFailureCode
import io.ktor.foodies.events.common.PaymentMethodInfo
import io.ktor.foodies.events.common.PaymentMethodType
import io.ktor.foodies.events.order.OrderStockConfirmedEvent
import io.ktor.foodies.events.payment.OrderPaymentFailedEvent
import io.ktor.foodies.events.payment.OrderPaymentSucceededEvent
import io.ktor.foodies.payment.PaymentStatus
import io.ktor.foodies.payment.serviceContext
import io.ktor.foodies.payment.testPaymentService
import io.ktor.foodies.rabbitmq.Publisher
import io.ktor.foodies.rabbitmq.RabbitMQSubscriber
import io.ktor.foodies.rabbitmq.subscribe
import io.ktor.foodies.server.test.channel
import io.ktor.foodies.server.test.ctxSuite
import jdk.internal.net.http.common.Log.channel
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json

private val TEST_VISA = PaymentMethodInfo(
    type = PaymentMethodType.CREDIT_CARD,
    cardLastFour = "4242",
    cardBrand = CardBrand.VISA,
    cardHolderName = "John Doe",
    expirationMonth = 12,
    expirationYear = 2025,
)

val orderStockConfirmedHandlerSpec by ctxSuite(context = { serviceContext() }) {
    testPaymentService("successful payment flow publishes OrderPaymentSucceededEvent") { (module, rabbit) ->
        val connectionFactory = rabbit.connectionFactory()
        val exchangeName = "test.handler.exchange.success"
        val queueName = "test.handler.queue.success"
        connectionFactory.channel { channel ->
            channel.exchangeDeclare(exchangeName, "topic", true)
            channel.queueDeclare(queueName, true, false, false, null)
            channel.queueBind(queueName, exchangeName, OrderPaymentSucceededEvent.key().key)
        }
        connectionFactory.newConnection().use { connection ->
            connection.createChannel().use { channel ->
                val publisher = RabbitMQPaymentEventPublisher(Publisher(channel, exchangeName, Json))
                OrderStockConfirmedEvent(
                    eventId = "evt-1",
                    orderId = 1L,
                    buyerId = "user-1",
                    totalAmount = BigDecimal("100.00"),
                    currency = "USD",
                    paymentMethod = TEST_VISA,
                    occurredAt = Instant.parse("2024-01-01T00:00:00Z"),
                ).handle(module.paymentService, publisher)
            }
        }
        connectionFactory.newConnection().use { connection ->
            val message = RabbitMQSubscriber(connection, exchangeName)
                .subscribe(OrderPaymentSucceededEvent.key(), queueName)
                .first()
            assertEquals(1L, message.value.orderId)
            message.ack()
        }
        val stored = module.paymentRepository.findByOrderId(1L)
        assertNotNull(stored)
        assertEquals(PaymentStatus.SUCCEEDED, stored.status)
    }
    testPaymentService(
        "declined card publishes OrderPaymentFailedEvent",
    ) { (module, rabbit) ->
        val connectionFactory = rabbit.connectionFactory()
        val exchangeName = "test.handler.exchange.failed"
        val queueName = "test.handler.queue.failed"
        connectionFactory.channel { channel ->
            channel.exchangeDeclare(exchangeName, "topic", true)
            channel.queueDeclare(queueName, true, false, false, null)
            channel.queueBind(queueName, exchangeName, OrderPaymentFailedEvent.key().key)
        }
        connectionFactory.newConnection().use { connection ->
            connection.createChannel().use { channel ->
                val publisher = RabbitMQPaymentEventPublisher(Publisher(channel, exchangeName, Json))
                OrderStockConfirmedEvent(
                    eventId = "evt-2",
                    orderId = 2L,
                    buyerId = "user-1",
                    totalAmount = BigDecimal("100.00"),
                    currency = "USD",
                    paymentMethod = PaymentMethodInfo(
                        type = PaymentMethodType.CREDIT_CARD,
                        cardLastFour = "0000",
                        cardBrand = CardBrand.VISA,
                        cardHolderName = "John Doe",
                        expirationMonth = 12,
                        expirationYear = 2025,
                    ),
                    occurredAt = Instant.parse("2024-01-01T00:00:00Z"),
                ).handle(module.paymentService, publisher)
            }
        }
        connectionFactory.newConnection().use { connection ->
            val message = RabbitMQSubscriber(connection, exchangeName)
                .subscribe(OrderPaymentFailedEvent.key(), queueName)
                .first()
            assertEquals(2L, message.value.orderId)
            assertEquals(PaymentFailureCode.CARD_DECLINED, message.value.failureCode)
            message.ack()
        }
        val stored = module.paymentRepository.findByOrderId(2L)
        assertNotNull(stored)
        assertEquals(PaymentStatus.FAILED, stored.status)
    }
    testPaymentService("idempotent handling publishes OrderPaymentSucceededEvent for each delivery") { (module, rabbit) ->
        val connectionFactory = rabbit.connectionFactory()
        val exchangeName = "test.handler.exchange.idempotent"
        val queueName = "test.handler.queue.idempotent"
        connectionFactory.channel { channel ->
            channel.exchangeDeclare(exchangeName, "topic", true)
            channel.queueDeclare(queueName, true, false, false, null)
            channel.queueBind(queueName, exchangeName, OrderPaymentSucceededEvent.key().key)
        }
        val event = OrderStockConfirmedEvent(
            eventId = "evt-3",
            orderId = 3L,
            buyerId = "user-1",
            totalAmount = BigDecimal("100.00"),
            currency = "USD",
            paymentMethod = TEST_VISA,
            occurredAt = Instant.parse("2024-01-01T00:00:00Z"),
        )
        connectionFactory.channel { channel ->
            val publisher = RabbitMQPaymentEventPublisher(Publisher(channel, exchangeName, Json))
            event.handle(module.paymentService, publisher)
            event.handle(module.paymentService, publisher)
        }
        connectionFactory.newConnection().use { connection ->
            val (first, second) = RabbitMQSubscriber(connection, exchangeName)
                .subscribe(OrderPaymentSucceededEvent.key(), queueName)
                .take(2)
                .toList()
            assertEquals(3L, first.value.orderId)
            val firstPaymentId = first.value.paymentId
            first.ack()
            assertEquals(3L, second.value.orderId)
            assertEquals(firstPaymentId, second.value.paymentId)
            second.ack()
        }
    }
}

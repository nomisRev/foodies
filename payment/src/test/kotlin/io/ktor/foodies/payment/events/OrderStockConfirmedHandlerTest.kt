package io.ktor.foodies.payment.events

import io.ktor.foodies.payment.*
import io.ktor.foodies.payment.gateway.SimulatedPaymentGateway
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Instant

class OrderStockConfirmedHandlerTest {

    private class InMemoryPaymentRepository : PaymentRepository {
        private val payments = ConcurrentHashMap<Long, PaymentRecord>()
        private val nextId = AtomicLong(1)

        override fun create(payment: PaymentRecord): PaymentRecord {
            val id = nextId.getAndIncrement()
            val created = payment.copy(id = id)
            payments[id] = created
            return created
        }

        override fun findById(id: Long): PaymentRecord? = payments[id]

        override fun findByOrderId(orderId: Long): PaymentRecord? =
            payments.values.find { it.orderId == orderId }

        override fun findByBuyerId(buyerId: String, limit: Int, offset: Int): List<PaymentRecord> =
            payments.values.filter { it.buyerId == buyerId }
                .drop(offset)
                .take(limit)

        override fun updateStatus(
            paymentId: Long,
            status: PaymentStatus,
            transactionId: String?,
            failureReason: String?,
            processedAt: Instant?
        ): Boolean {
            val payment = payments[paymentId] ?: return false
            payments[paymentId] = payment.copy(
                status = status,
                transactionId = transactionId ?: payment.transactionId,
                failureReason = failureReason ?: payment.failureReason,
                processedAt = processedAt ?: payment.processedAt
            )
            return true
        }
    }

    private class InMemoryEventPublisher : EventPublisher {
        val publishedEvents = mutableListOf<Any>()

        override suspend fun publish(event: OrderPaymentSucceededEvent) {
            publishedEvents.add(event)
        }

        override suspend fun publish(event: OrderPaymentFailedEvent) {
            publishedEvents.add(event)
        }

        override fun close() {}
    }

    @Test
    fun `successful payment flow`() = runTest {
        val repository = InMemoryPaymentRepository()
        val gateway = SimulatedPaymentGateway(PaymentGatewayConfig(alwaysSucceed = true, processingDelayMs = 0))
        val service = PaymentServiceImpl(repository, gateway)
        val publisher = InMemoryEventPublisher()
        val handler = OrderStockConfirmedEventHandler(service, publisher)

        val event = OrderStockConfirmedEvent(
            eventId = "evt-1",
            orderId = 1L,
            buyerId = "user-1",
            totalAmount = BigDecimal("100.00"),
            currency = "USD",
            paymentMethod = PaymentMethodInfo(
                type = PaymentMethodType.CREDIT_CARD,
                cardLastFour = "4242",
                cardBrand = CardBrand.VISA,
                cardHolderName = "John Doe",
                expirationMonth = 12,
                expirationYear = 2025
            ),
            occurredAt = Instant.parse("2024-01-01T00:00:00Z")
        )

        handler.handle(event)

        assertEquals(1, publisher.publishedEvents.size)
        assertTrue(publisher.publishedEvents[0] is OrderPaymentSucceededEvent)
        val successEvent = publisher.publishedEvents[0] as OrderPaymentSucceededEvent
        assertEquals(1L, successEvent.orderId)
    }

    @Test
    fun `failed payment flow`() = runTest {
        val repository = InMemoryPaymentRepository()
        val gateway = SimulatedPaymentGateway(PaymentGatewayConfig(alwaysSucceed = false, processingDelayMs = 0))
        val service = PaymentServiceImpl(repository, gateway)
        val publisher = InMemoryEventPublisher()
        val handler = OrderStockConfirmedEventHandler(service, publisher)

        val event = OrderStockConfirmedEvent(
            eventId = "evt-2",
            orderId = 2L,
            buyerId = "user-1",
            totalAmount = BigDecimal("100.00"),
            currency = "USD",
            paymentMethod = PaymentMethodInfo(
                type = PaymentMethodType.CREDIT_CARD,
                cardLastFour = "0000", // Will fail in SimulatedPaymentGateway
                cardBrand = CardBrand.VISA,
                cardHolderName = "John Doe",
                expirationMonth = 12,
                expirationYear = 2025
            ),
            occurredAt = Instant.parse("2024-01-01T00:00:00Z")
        )

        handler.handle(event)

        assertEquals(1, publisher.publishedEvents.size)
        assertTrue(publisher.publishedEvents[0] is OrderPaymentFailedEvent)
        val failedEvent = publisher.publishedEvents[0] as OrderPaymentFailedEvent
        assertEquals(2L, failedEvent.orderId)
        assertEquals(PaymentFailureCode.CARD_DECLINED, failedEvent.failureCode)
    }

    @Test
    fun `idempotent event handling`() = runTest {
        val repository = InMemoryPaymentRepository()
        val gateway = SimulatedPaymentGateway(PaymentGatewayConfig(alwaysSucceed = true, processingDelayMs = 0))
        val service = PaymentServiceImpl(repository, gateway)
        val publisher = InMemoryEventPublisher()
        val handler = OrderStockConfirmedEventHandler(service, publisher)

        val event = OrderStockConfirmedEvent(
            eventId = "evt-3",
            orderId = 3L,
            buyerId = "user-1",
            totalAmount = BigDecimal("100.00"),
            currency = "USD",
            paymentMethod = PaymentMethodInfo(
                type = PaymentMethodType.CREDIT_CARD,
                cardLastFour = "4242",
                cardBrand = CardBrand.VISA,
                cardHolderName = "John Doe",
                expirationMonth = 12,
                expirationYear = 2025
            ),
            occurredAt = Instant.parse("2024-01-01T00:00:00Z")
        )

        // Handle twice
        handler.handle(event)
        handler.handle(event)

        assertEquals(2, publisher.publishedEvents.size)
        assertTrue(publisher.publishedEvents[0] is OrderPaymentSucceededEvent)
        assertTrue(publisher.publishedEvents[1] is OrderPaymentSucceededEvent)
        
        val firstEvent = publisher.publishedEvents[0] as OrderPaymentSucceededEvent
        val secondEvent = publisher.publishedEvents[1] as OrderPaymentSucceededEvent
        
        assertEquals(firstEvent.orderId, secondEvent.orderId)
        assertEquals(firstEvent.paymentId, secondEvent.paymentId)
    }
}

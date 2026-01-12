package io.ktor.foodies.payment

import io.ktor.foodies.payment.gateway.PaymentGateway
import io.ktor.foodies.payment.gateway.SimulatedPaymentGateway
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Instant

class PaymentServiceTest {

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

    @Test
    fun `successful payment publishes success event`() = runTest {
        val repository = InMemoryPaymentRepository()
        val gateway = SimulatedPaymentGateway(PaymentGatewayConfig(alwaysSucceed = true, processingDelayMs = 0))
        val service = PaymentServiceImpl(repository, gateway)

        val result = service.processPayment(
            ProcessPaymentRequest(
                eventId = "evt-123",
                orderId = 1L,
                buyerId = "user-123",
                amount = BigDecimal("49.99"),
                currency = "USD",
                paymentMethod = PaymentMethodInfo(
                    type = PaymentMethodType.CREDIT_CARD,
                    cardLastFour = "4242",
                    cardBrand = CardBrand.VISA,
                    cardHolderName = "John Doe",
                    expirationMonth = 12,
                    expirationYear = 2025
                )
            )
        )

        assertTrue(result is PaymentResult.Success)
        val payment = repository.findByOrderId(1L)
        assertNotNull(payment)
        assertEquals(PaymentStatus.SUCCEEDED, payment.status)
    }

    @Test
    fun `duplicate payment request returns already processed`() = runTest {
        val repository = InMemoryPaymentRepository()
        val gateway = SimulatedPaymentGateway(PaymentGatewayConfig(alwaysSucceed = true, processingDelayMs = 0))
        val service = PaymentServiceImpl(repository, gateway)

        val request = ProcessPaymentRequest(
            eventId = "evt-123",
            orderId = 1L,
            buyerId = "user-123",
            amount = BigDecimal("49.99"),
            currency = "USD",
            paymentMethod = PaymentMethodInfo(
                type = PaymentMethodType.CREDIT_CARD,
                cardLastFour = "4242",
                cardBrand = CardBrand.VISA,
                cardHolderName = "John Doe",
                expirationMonth = 12,
                expirationYear = 2025
            )
        )

        // First call succeeds
        val first = service.processPayment(request)
        assertTrue(first is PaymentResult.Success)

        // Second call returns already processed
        val second = service.processPayment(request)
        assertTrue(second is PaymentResult.AlreadyProcessed)
    }

    @Test
    fun `failed payment updates repository with failure`() = runTest {
        val repository = InMemoryPaymentRepository()
        val gateway = SimulatedPaymentGateway(PaymentGatewayConfig(alwaysSucceed = false, processingDelayMs = 0))
        val service = PaymentServiceImpl(repository, gateway)

        val result = service.processPayment(
            ProcessPaymentRequest(
                eventId = "evt-123",
                orderId = 1L,
                buyerId = "user-123",
                amount = BigDecimal("49.99"),
                currency = "USD",
                paymentMethod = PaymentMethodInfo(
                    type = PaymentMethodType.CREDIT_CARD,
                    cardLastFour = "0000", // Will fail in SimulatedPaymentGateway
                    cardBrand = CardBrand.VISA,
                    cardHolderName = "John Doe",
                    expirationMonth = 12,
                    expirationYear = 2025
                )
            )
        )

        assertTrue(result is PaymentResult.Failed)
        assertEquals(PaymentFailureCode.CARD_DECLINED, result.code)
        
        val payment = repository.findByOrderId(1L)
        assertNotNull(payment)
        assertEquals(PaymentStatus.FAILED, payment.status)
        assertEquals("Card declined", payment.failureReason)
    }
}

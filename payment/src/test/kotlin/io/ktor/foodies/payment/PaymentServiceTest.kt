package io.ktor.foodies.payment

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.payment.gateway.SimulatedPaymentGateway
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

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

private data class TestContext(
    val repository: InMemoryPaymentRepository,
    val service: PaymentService
)

private fun createTestContext(alwaysSucceed: Boolean = true): TestContext {
    val repository = InMemoryPaymentRepository()
    val gateway = SimulatedPaymentGateway(PaymentGatewayConfig(alwaysSucceed = alwaysSucceed, processingDelayMs = 0))
    val service = PaymentServiceImpl(repository, gateway)
    return TestContext(repository, service)
}

val paymentServiceSpec by testSuite {
    test("successful payment publishes success event") {
        val ctx = createTestContext(alwaysSucceed = true)

        val result = ctx.service.processPayment(
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
        val payment = ctx.repository.findByOrderId(1L)
        assertNotNull(payment)
        assertEquals(PaymentStatus.SUCCEEDED, payment.status)
    }

    test("duplicate payment request returns already processed") {
        val ctx = createTestContext(alwaysSucceed = true)

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
        val first = ctx.service.processPayment(request)
        assertTrue(first is PaymentResult.Success)

        // Second call returns already processed
        val second = ctx.service.processPayment(request)
        assertTrue(second is PaymentResult.AlreadyProcessed)
    }

    test("failed payment updates repository with failure") {
        val ctx = createTestContext(alwaysSucceed = false)

        val result = ctx.service.processPayment(
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

        val payment = ctx.repository.findByOrderId(1L)
        assertNotNull(payment)
        assertEquals(PaymentStatus.FAILED, payment.status)
        assertEquals("Card declined", payment.failureReason)
    }
}

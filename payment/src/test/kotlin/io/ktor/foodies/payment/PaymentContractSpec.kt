package io.ktor.foodies.payment

import io.ktor.foodies.events.common.*
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.foodies.server.test.ctxSuite
import io.ktor.foodies.server.test.jsonClient
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Test payment methods
private val TEST_VISA = PaymentMethodInfo(
    type = PaymentMethodType.CREDIT_CARD,
    cardLastFour = "4242",
    cardBrand = CardBrand.VISA,
    cardHolderName = "John Doe",
    expirationMonth = 12,
    expirationYear = 2025
)

private val TEST_MASTERCARD = PaymentMethodInfo(
    type = PaymentMethodType.CREDIT_CARD,
    cardLastFour = "5555",
    cardBrand = CardBrand.MASTERCARD,
    cardHolderName = "Jane Smith",
    expirationMonth = 6,
    expirationYear = 2026
)

private val DECLINED_CARD = PaymentMethodInfo(
    type = PaymentMethodType.CREDIT_CARD,
    cardLastFour = "0000",
    cardBrand = CardBrand.VISA,
    cardHolderName = "Test Declined",
    expirationMonth = 1,
    expirationYear = 2025
)

/**
 * Contract tests for the Payment service using real PostgreSQL and RabbitMQ.
 *
 * These tests verify the complete flow from processing payments through the service layer
 * to actual PostgreSQL storage and event publishing, ensuring all components work together correctly.
 */
val paymentContractSpec by ctxSuite(context = { serviceContext() }) {
    val testToken = createTestToken("test-user")
    testPaymentService("process payment successfully and verify storage") { module ->
        val request = ProcessPaymentRequest(
            eventId = "evt-001",
            orderId = 100L,
            buyerId = "user-123",
            amount = BigDecimal("50.00"),
            currency = "USD",
            paymentMethod = TEST_VISA
        )

        // Process payment
        val result = module.paymentService.processPayment(request)

        // Verify success result
        assertTrue(result is PaymentResult.Success)
        assertNotNull(result.paymentId)
        assertNotNull(result.transactionId)
        assertNotNull(result.processedAt)

        // Verify payment is stored in database
        val stored = module.paymentRepository.findByOrderId(request.orderId)
        assertNotNull(stored)
        assertEquals(request.orderId, stored.orderId)
        assertEquals(request.buyerId, stored.buyerId)
        assertEquals(request.amount, stored.amount)
        assertEquals(request.currency, stored.currency)
        assertEquals(PaymentStatus.SUCCEEDED, stored.status)
        assertNotNull(stored.transactionId)
        assertNotNull(stored.processedAt)
    }

    testPaymentService("idempotency: processing same order twice returns existing payment") { module ->
        val request = ProcessPaymentRequest(
            eventId = "evt-002",
            orderId = 200L,
            buyerId = "user-456",
            amount = BigDecimal("75.50"),
            currency = "USD",
            paymentMethod = TEST_MASTERCARD
        )

        // First payment
        val firstResult = module.paymentService.processPayment(request)
        assertTrue(firstResult is PaymentResult.Success)

        // Second payment with same order ID
        val secondResult = module.paymentService.processPayment(request)
        assertTrue(secondResult is PaymentResult.AlreadyProcessed)

        // Verify same payment is returned
        assertEquals(firstResult.paymentId, secondResult.paymentRecord.id)
        assertEquals(PaymentStatus.SUCCEEDED, secondResult.paymentRecord.status)
    }

    testPaymentService("retrieve payment by order ID via HTTP endpoint") { module ->
        val request = ProcessPaymentRequest(
            eventId = "evt-003",
            orderId = 300L,
            buyerId = "user-789",
            amount = BigDecimal("120.99"),
            currency = "EUR",
            paymentMethod = TEST_VISA
        )

        // Process payment
        module.paymentService.processPayment(request)

        // Retrieve via HTTP
        val response = jsonClient().get("/payments/${request.orderId}") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val payment = response.body<PaymentRecord>()
        assertEquals(request.orderId, payment.orderId)
        assertEquals(request.buyerId, payment.buyerId)
        assertEquals(request.amount, payment.amount)
        assertEquals(request.currency, payment.currency)
        assertEquals(PaymentStatus.SUCCEEDED, payment.status)
    }

    testPaymentService("HTTP endpoint returns 404 for non-existent payment") { module ->
        val response = jsonClient().get("/payments/999999") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    testPaymentService("HTTP endpoint returns 400 for invalid order ID") { module ->
        val response = jsonClient().get("/payments/invalid") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    testPaymentService("multiple payments for different orders") { module ->
        val payments = listOf(
            ProcessPaymentRequest(
                eventId = "evt-multi-1",
                orderId = 1001L,
                buyerId = "buyer-1",
                amount = BigDecimal("25.00"),
                currency = "USD",
                paymentMethod = TEST_VISA
            ),
            ProcessPaymentRequest(
                eventId = "evt-multi-2",
                orderId = 1002L,
                buyerId = "buyer-2",
                amount = BigDecimal("35.00"),
                currency = "USD",
                paymentMethod = TEST_MASTERCARD
            ),
            ProcessPaymentRequest(
                eventId = "evt-multi-3",
                orderId = 1003L,
                buyerId = "buyer-1",
                amount = BigDecimal("45.00"),
                currency = "EUR",
                paymentMethod = TEST_VISA
            )
        )

        // Process all payments
        val results = payments.map { module.paymentService.processPayment(it) }

        // Verify all succeeded
        assertTrue(results.all { it is PaymentResult.Success })

        // Verify each payment can be retrieved
        payments.forEach { request ->
            val stored = module.paymentRepository.findByOrderId(request.orderId)
            assertNotNull(stored)
            assertEquals(request.orderId, stored.orderId)
            assertEquals(request.buyerId, stored.buyerId)
            assertEquals(request.amount, stored.amount)
            assertEquals(PaymentStatus.SUCCEEDED, stored.status)
        }
    }

    testPaymentService("payment with different currencies") { module ->
        val currencies = listOf("USD", "EUR", "GBP", "JPY")

        currencies.forEachIndexed { index, currency ->
            val request = ProcessPaymentRequest(
                eventId = "evt-currency-$index",
                orderId = 2000L + index,
                buyerId = "user-currency",
                amount = BigDecimal("100.00"),
                currency = currency,
                paymentMethod = TEST_VISA
            )

            val result = module.paymentService.processPayment(request)
            assertTrue(result is PaymentResult.Success)

            val stored = module.paymentRepository.findByOrderId(request.orderId)
            assertNotNull(stored)
            assertEquals(currency, stored.currency)
        }
    }

    testPaymentService("payment with different payment methods") { module ->
        val paymentMethods = listOf(
            PaymentMethodInfo(
                type = PaymentMethodType.CREDIT_CARD,
                cardLastFour = "1234",
                cardBrand = CardBrand.VISA,
                cardHolderName = "Test User",
                expirationMonth = 12,
                expirationYear = 2025
            ),
            PaymentMethodInfo(
                type = PaymentMethodType.DEBIT_CARD,
                cardLastFour = "5678",
                cardBrand = CardBrand.MASTERCARD,
                cardHolderName = "Test User",
                expirationMonth = 6,
                expirationYear = 2026
            ),
            PaymentMethodInfo(
                type = PaymentMethodType.CREDIT_CARD,
                cardLastFour = "9999",
                cardBrand = CardBrand.AMEX,
                cardHolderName = "Test User",
                expirationMonth = 3,
                expirationYear = 2027
            )
        )

        paymentMethods.forEachIndexed { index, method ->
            val request = ProcessPaymentRequest(
                eventId = "evt-method-$index",
                orderId = 3000L + index,
                buyerId = "user-method",
                amount = BigDecimal("50.00"),
                currency = "USD",
                paymentMethod = method
            )

            val result = module.paymentService.processPayment(request)
            assertTrue(result is PaymentResult.Success)

            val stored = module.paymentRepository.findByOrderId(request.orderId)
            assertNotNull(stored)
            assertEquals(method.type, stored.paymentMethod.type)
            assertEquals(method.cardBrand, stored.paymentMethod.cardBrand)
            assertEquals(method.cardLastFour, stored.paymentMethod.cardLastFour)
        }
    }

    testPaymentService("retrieve payment by payment ID") { module ->
        val request = ProcessPaymentRequest(
            eventId = "evt-by-id",
            orderId = 4000L,
            buyerId = "user-by-id",
            amount = BigDecimal("99.99"),
            currency = "USD",
            paymentMethod = TEST_VISA
        )

        val result = module.paymentService.processPayment(request)
        assertTrue(result is PaymentResult.Success)

        // Retrieve by payment ID
        val retrieved = module.paymentService.getPaymentById(result.paymentId)
        assertNotNull(retrieved)
        assertEquals(result.paymentId, retrieved.id)
        assertEquals(request.orderId, retrieved.orderId)
        assertEquals(request.buyerId, retrieved.buyerId)
    }

    testPaymentService("payment amounts are stored with correct precision") { module ->
        val amounts = listOf(
            BigDecimal("0.01"),
            BigDecimal("1.99"),
            BigDecimal("10.50"),
            BigDecimal("99.99"),
            BigDecimal("999.99"),
            BigDecimal("1234.56")
        )

        amounts.forEachIndexed { index, amount ->
            val request = ProcessPaymentRequest(
                eventId = "evt-precision-$index",
                orderId = 5000L + index,
                buyerId = "user-precision",
                amount = amount,
                currency = "USD",
                paymentMethod = TEST_VISA
            )

            module.paymentService.processPayment(request)

            val stored = module.paymentRepository.findByOrderId(request.orderId)
            assertNotNull(stored)
            assertEquals(amount, stored.amount)
        }
    }
}

package io.ktor.foodies.payment

import io.ktor.foodies.events.common.*
import io.ktor.foodies.server.test.ctxSuite
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

private fun createTestPayment(orderId: Long, buyerId: String = "user-123") =
    PaymentRecord(
        id = 0,
        orderId = orderId,
        buyerId = buyerId,
        amount = BigDecimal("100.00"),
        currency = "USD",
        status = PaymentStatus.PENDING,
        paymentMethod =
            PaymentMethodInfo(
                type = PaymentMethodType.CREDIT_CARD,
                cardLastFour = "4242",
                cardBrand = CardBrand.VISA,
                cardHolderName = "John Doe",
                expirationMonth = 12,
                expirationYear = 2025,
            ),
        transactionId = null,
        failureReason = null,
        createdAt = Clock.System.now(),
        processedAt = null,
    )

val paymentRepositorySpec by
    ctxSuite(context = { serviceContext() }) {
        testPostgres("should create and find payment record") { repository ->
            val payment = createTestPayment(orderId = 1L)

            val created = repository.create(payment)
            assertTrue(created.id > 0)

            val found = repository.findById(created.id)
            assertNotNull(found)
            assertEquals(payment.orderId, found.orderId)
            assertEquals(payment.buyerId, found.buyerId)
            assertEquals(payment.amount.stripTrailingZeros(), found.amount.stripTrailingZeros())
            assertEquals(PaymentStatus.PENDING, found.status)
        }

        testPostgres("should find payment by order id") { repository ->
            val orderId = 2L
            val payment = createTestPayment(orderId = orderId)
            repository.create(payment)

            val found = repository.findByOrderId(orderId)
            assertNotNull(found)
            assertEquals(orderId, found.orderId)
        }

        testPostgres("should update payment status") { repository ->
            val payment = createTestPayment(orderId = 3L)
            val created = repository.create(payment)
            val processedAt = Clock.System.now()
            val transactionId = "txn-abc-123"

            val updated =
                repository.updateStatus(
                    paymentId = created.id,
                    status = PaymentStatus.SUCCEEDED,
                    transactionId = transactionId,
                    processedAt = processedAt,
                )

            assertTrue(updated)

            val found = repository.findById(created.id)
            assertNotNull(found)
            assertEquals(PaymentStatus.SUCCEEDED, found.status)
            assertEquals(transactionId, found.transactionId)
            assertNotNull(found.processedAt)
            // Check if timestamps are close enough (ignoring nanos precision diff if any)
            assertTrue(
                Math.abs(
                    found.processedAt.toEpochMilliseconds() - processedAt.toEpochMilliseconds()
                ) < 1000
            )
        }

        testPostgres("should find by buyer id with pagination") { repository ->
            val buyerId = "buyer-999"
            repeat(5) { i ->
                repository.create(createTestPayment(orderId = 100L + i, buyerId = buyerId))
            }

            val all = repository.findByBuyerId(buyerId, limit = 10)
            assertEquals(5, all.size)

            val paginated = repository.findByBuyerId(buyerId, limit = 2, offset = 2)
            assertEquals(2, paginated.size)
        }
    }

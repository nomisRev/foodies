package io.ktor.foodies.payment

import io.ktor.foodies.events.common.CardBrand
import io.ktor.foodies.events.common.PaymentFailureCode
import io.ktor.foodies.events.common.PaymentMethodInfo
import io.ktor.foodies.events.common.PaymentMethodType
import io.ktor.foodies.server.test.ctxSuite
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val TEST_VISA = PaymentMethodInfo(
    type = PaymentMethodType.CREDIT_CARD,
    cardLastFour = "4242",
    cardBrand = CardBrand.VISA,
    cardHolderName = "John Doe",
    expirationMonth = 12,
    expirationYear = 2025,
)

val paymentServiceSpec by ctxSuite(context = { serviceContext() }) {
    testPaymentService("successful payment stores SUCCEEDED record") { (module) ->
        val result = module.paymentService.processPayment(
            ProcessPaymentRequest(
                eventId = "evt-123",
                orderId = 10001L,
                buyerId = "user-123",
                amount = BigDecimal("49.99"),
                currency = "USD",
                paymentMethod = TEST_VISA,
            )
        )

        assertTrue(result is PaymentResult.Success)
        val payment = module.paymentRepository.findByOrderId(10001L)
        assertNotNull(payment)
        assertEquals(PaymentStatus.SUCCEEDED, payment.status)
        assertNotNull(payment.transactionId)
    }

    testPaymentService("duplicate payment request returns AlreadyProcessed") { (module) ->
        val request = ProcessPaymentRequest(
            eventId = "evt-124",
            orderId = 10002L,
            buyerId = "user-123",
            amount = BigDecimal("49.99"),
            currency = "USD",
            paymentMethod = TEST_VISA,
        )

        val first = module.paymentService.processPayment(request)
        assertTrue(first is PaymentResult.Success)

        val second = module.paymentService.processPayment(request)
        assertTrue(second is PaymentResult.AlreadyProcessed)
        assertEquals(first.paymentId, second.paymentRecord.id)
    }

    testPaymentService("declined card stores FAILED record") { (module) ->
        val result = module.paymentService.processPayment(
            ProcessPaymentRequest(
                eventId = "evt-125",
                orderId = 10003L,
                buyerId = "user-123",
                amount = BigDecimal("49.99"),
                currency = "USD",
                paymentMethod = PaymentMethodInfo(
                    type = PaymentMethodType.CREDIT_CARD,
                    cardLastFour = "0000",
                    cardBrand = CardBrand.VISA,
                    cardHolderName = "John Doe",
                    expirationMonth = 12,
                    expirationYear = 2025,
                ),
            )
        )

        assertTrue(result is PaymentResult.Failed)
        assertEquals(PaymentFailureCode.CARD_DECLINED, result.code)

        val payment = module.paymentRepository.findByOrderId(10003L)
        assertNotNull(payment)
        assertEquals(PaymentStatus.FAILED, payment.status)
        assertEquals("Card declined", payment.failureReason)
    }
}

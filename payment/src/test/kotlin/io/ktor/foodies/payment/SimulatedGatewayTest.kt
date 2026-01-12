package io.ktor.foodies.payment

import io.ktor.foodies.payment.gateway.ChargeRequest
import io.ktor.foodies.payment.gateway.GatewayResult
import io.ktor.foodies.payment.gateway.SimulatedPaymentGateway
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulatedGatewayTest {

    @Test
    fun `alwaysSucceed returns success regardless of card number`() = runTest {
        val config = PaymentGatewayConfig(alwaysSucceed = true, processingDelayMs = 0)
        val gateway = SimulatedPaymentGateway(config)
        
        val request = createRequest(cardLastFour = "0000")
        val result = gateway.charge(request)
        
        assertTrue(result is GatewayResult.Success)
        assertTrue(result.transactionId.startsWith("txn_"))
    }

    @Test
    fun `card number 0000 returns card declined when alwaysSucceed is false`() = runTest {
        val config = PaymentGatewayConfig(alwaysSucceed = false, processingDelayMs = 0)
        val gateway = SimulatedPaymentGateway(config)
        
        val request = createRequest(cardLastFour = "0000")
        val result = gateway.charge(request)
        
        assertTrue(result is GatewayResult.Failed)
        assertEquals(PaymentFailureCode.CARD_DECLINED, result.code)
        assertEquals("Card declined", result.reason)
    }

    @Test
    fun `card number 1111 returns insufficient funds when alwaysSucceed is false`() = runTest {
        val config = PaymentGatewayConfig(alwaysSucceed = false, processingDelayMs = 0)
        val gateway = SimulatedPaymentGateway(config)
        
        val request = createRequest(cardLastFour = "1111")
        val result = gateway.charge(request)
        
        assertTrue(result is GatewayResult.Failed)
        assertEquals(PaymentFailureCode.INSUFFICIENT_FUNDS, result.code)
        assertEquals("Insufficient funds", result.reason)
    }

    @Test
    fun `card number 2222 returns card expired when alwaysSucceed is false`() = runTest {
        val config = PaymentGatewayConfig(alwaysSucceed = false, processingDelayMs = 0)
        val gateway = SimulatedPaymentGateway(config)
        
        val request = createRequest(cardLastFour = "2222")
        val result = gateway.charge(request)
        
        assertTrue(result is GatewayResult.Failed)
        assertEquals(PaymentFailureCode.CARD_EXPIRED, result.code)
        assertEquals("Card expired", result.reason)
    }

    @Test
    fun `other card numbers return success when alwaysSucceed is false`() = runTest {
        val config = PaymentGatewayConfig(alwaysSucceed = false, processingDelayMs = 0)
        val gateway = SimulatedPaymentGateway(config)
        
        val request = createRequest(cardLastFour = "4242")
        val result = gateway.charge(request)
        
        assertTrue(result is GatewayResult.Success)
        assertTrue(result.transactionId.startsWith("txn_"))
    }

    private fun createRequest(cardLastFour: String) = ChargeRequest(
        amount = BigDecimal("49.99"),
        currency = "USD",
        paymentMethod = PaymentMethodInfo(
            type = PaymentMethodType.CREDIT_CARD,
            cardLastFour = cardLastFour,
            cardBrand = CardBrand.VISA,
            cardHolderName = "John Doe",
            expirationMonth = 12,
            expirationYear = 2025
        ),
        orderId = 1L,
        buyerId = "user-123"
    )
}

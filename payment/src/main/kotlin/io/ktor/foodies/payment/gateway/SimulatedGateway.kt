package io.ktor.foodies.payment.gateway

import io.ktor.foodies.payment.PaymentFailureCode
import io.ktor.foodies.payment.PaymentGatewayConfig
import kotlinx.coroutines.delay
import java.util.UUID

class SimulatedPaymentGateway(
    private val config: PaymentGatewayConfig
) : PaymentGateway {

    override suspend fun charge(request: ChargeRequest): GatewayResult {
        // Simulate processing delay
        delay(config.processingDelayMs)

        // Configurable success/failure for testing
        return if (config.alwaysSucceed) {
            GatewayResult.Success(
                transactionId = "txn_${UUID.randomUUID()}"
            )
        } else {
            // Simulate various failure scenarios based on card number patterns
            simulateCardBehavior(request)
        }
    }

    private fun simulateCardBehavior(request: ChargeRequest): GatewayResult {
        // Test card numbers for different scenarios
        return when {
            request.paymentMethod.cardLastFour == "0000" ->
                GatewayResult.Failed("Card declined", PaymentFailureCode.CARD_DECLINED)
            request.paymentMethod.cardLastFour == "1111" ->
                GatewayResult.Failed("Insufficient funds", PaymentFailureCode.INSUFFICIENT_FUNDS)
            request.paymentMethod.cardLastFour == "2222" ->
                GatewayResult.Failed("Card expired", PaymentFailureCode.CARD_EXPIRED)
            else ->
                GatewayResult.Success(transactionId = "txn_${UUID.randomUUID()}")
        }
    }
}

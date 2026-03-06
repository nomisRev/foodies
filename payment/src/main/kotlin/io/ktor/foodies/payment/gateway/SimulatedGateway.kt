package io.ktor.foodies.payment.gateway

import io.ktor.foodies.events.common.PaymentFailureCode
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class SimulatedPaymentGateway(
    private val processingDelay: Duration = 10.milliseconds,
    private val alwaysSucceed: Boolean = false
) : PaymentGateway {
    override suspend fun charge(request: ChargeRequest): GatewayResult {
        delay(processingDelay)
        return if (alwaysSucceed) GatewayResult.Success(transactionId = "txn_${UUID.randomUUID()}")
        else simulateCardBehavior(request.paymentMethod.cardLastFour)
    }

    private fun simulateCardBehavior(cardLastFour: String?): GatewayResult =
        when (cardLastFour) {
            "0000" -> GatewayResult.Failed("Card declined", PaymentFailureCode.CARD_DECLINED)
            "0001" -> GatewayResult.Failed("Insufficient funds", PaymentFailureCode.INSUFFICIENT_FUNDS)
            "0002" -> GatewayResult.Failed("Card expired", PaymentFailureCode.CARD_EXPIRED)
            else -> GatewayResult.Success(transactionId = "txn_${UUID.randomUUID()}")
        }
}

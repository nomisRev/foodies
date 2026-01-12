package io.ktor.foodies.payment.gateway

import io.ktor.foodies.payment.PaymentFailureCode
import io.ktor.foodies.payment.PaymentMethodInfo
import java.math.BigDecimal

interface PaymentGateway {
    suspend fun charge(request: ChargeRequest): GatewayResult
}

data class ChargeRequest(
    val amount: BigDecimal,
    val currency: String,
    val paymentMethod: PaymentMethodInfo,
    val orderId: Long,
    val buyerId: String
)

sealed interface GatewayResult {
    data class Success(val transactionId: String) : GatewayResult
    data class Failed(val reason: String, val code: PaymentFailureCode) : GatewayResult
}

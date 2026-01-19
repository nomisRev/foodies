package io.ktor.foodies.payment.gateway

import io.ktor.foodies.events.common.PaymentFailureCode
import io.ktor.foodies.events.common.PaymentMethodInfo
import io.ktor.foodies.server.SerializableBigDecimal

interface PaymentGateway {
    suspend fun charge(request: ChargeRequest): GatewayResult
}

data class ChargeRequest(
    val amount: SerializableBigDecimal,
    val currency: String,
    val paymentMethod: PaymentMethodInfo,
    val orderId: Long,
    val buyerId: String,
)

sealed interface GatewayResult {
    data class Success(val transactionId: String) : GatewayResult

    data class Failed(val reason: String, val code: PaymentFailureCode) : GatewayResult
}

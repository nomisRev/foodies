package io.ktor.foodies.events.common

import kotlinx.serialization.Serializable

@Serializable
enum class PaymentFailureCode {
    INSUFFICIENT_FUNDS,
    CARD_DECLINED,
    CARD_EXPIRED,
    INVALID_CARD,
    FRAUD_SUSPECTED,
    GATEWAY_ERROR,
    TIMEOUT,
    UNKNOWN,
}

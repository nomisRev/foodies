package io.ktor.foodies.events.common

import kotlinx.serialization.Serializable

@Serializable
enum class PaymentMethodType {
    CREDIT_CARD,
    DEBIT_CARD,
    DIGITAL_WALLET,
    BANK_TRANSFER,
}

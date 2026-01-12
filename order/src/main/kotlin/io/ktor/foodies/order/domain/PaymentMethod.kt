package io.ktor.foodies.order.domain

import kotlinx.serialization.Serializable

@Serializable
data class PaymentMethod(
    val id: Long,
    val cardType: CardType,
    val cardHolderName: String,
    val cardNumber: String,                     // Last 4 digits only (masked)
    val expirationMonth: Int,
    val expirationYear: Int,
)

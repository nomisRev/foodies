package io.ktor.foodies.events.common

import kotlinx.serialization.Serializable

@Serializable
data class PaymentMethodInfo(
    val type: PaymentMethodType,
    val cardLastFour: String?,
    val cardBrand: CardBrand?,
    val cardHolderName: String?,
    val expirationMonth: Int?,
    val expirationYear: Int?,
)

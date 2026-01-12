package io.ktor.foodies.order.domain

import kotlinx.serialization.Serializable

@Serializable
enum class CardBrand(val displayName: String) {
    VISA("Visa"),
    MASTERCARD("MasterCard"),
    AMEX("American Express"),
    DISCOVER("Discover"),
    UNKNOWN("Unknown")
}

@Serializable
data class CardBrandResponse(
    val id: String,
    val name: String,
)

package io.ktor.foodies.events.common

import kotlinx.serialization.Serializable

@Serializable
enum class CardBrand(val displayName: String) {
    VISA("Visa"),
    MASTERCARD("MasterCard"),
    AMEX("American Express"),
    DISCOVER("Discover"),
    UNKNOWN("Unknown"),
}

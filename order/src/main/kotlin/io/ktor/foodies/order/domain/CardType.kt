package io.ktor.foodies.order.domain

import kotlinx.serialization.Serializable

@Serializable
enum class CardType(val displayName: String) {
    Visa("Visa"),
    MasterCard("MasterCard"),
    Amex("American Express"),
}

@Serializable
data class CardTypeResponse(
    val id: String,
    val name: String,
)

package io.ktor.foodies.order.domain

import kotlinx.serialization.Serializable

@Serializable
data class CardBrandResponse(
    val id: String,
    val name: String,
)

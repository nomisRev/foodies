package io.ktor.foodies.order.domain

import kotlinx.serialization.Serializable

@Serializable
data class Address(
    val street: String,
    val city: String,
    val state: String,
    val country: String,
    val zipCode: String,
)

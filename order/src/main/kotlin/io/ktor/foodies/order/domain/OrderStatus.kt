package io.ktor.foodies.order.domain

import kotlinx.serialization.Serializable

@Serializable
enum class OrderStatus {
    Submitted,              // Initial state after order creation
    AwaitingValidation,     // Grace period ended, awaiting stock validation
    StockConfirmed,         // Menu service confirmed item availability
    Paid,                   // Payment confirmed
    Shipped,                // Order shipped to customer
    Cancelled               // Order cancelled (by user, stock rejection, or payment failure)
}

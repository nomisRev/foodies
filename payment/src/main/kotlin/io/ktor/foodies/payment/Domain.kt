package io.ktor.foodies.payment

import io.ktor.foodies.events.common.*
import io.ktor.foodies.server.SerializableBigDecimal
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class PaymentRecord(
    val id: Long,
    val orderId: Long,
    val buyerId: String,                          // Keycloak user ID
    val amount: SerializableBigDecimal,
    val currency: String,                          // ISO 4217 currency code
    val status: PaymentStatus,
    val paymentMethod: PaymentMethodInfo,
    val transactionId: String?,                    // External payment gateway reference
    val failureReason: String?,                    // Reason for failure if status is FAILED
    val createdAt: Instant,
    val processedAt: Instant?
)

@Serializable
enum class PaymentStatus {
    PENDING,      // Payment initiated, not yet processed
    PROCESSING,   // Payment being processed by gateway
    SUCCEEDED,    // Payment completed successfully
    FAILED,       // Payment failed
    REFUNDED      // Payment was refunded (future use)
}

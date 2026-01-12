package io.ktor.foodies.payment

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

@Serializable
data class PaymentMethodInfo(
    val type: PaymentMethodType,
    val cardLastFour: String?,                     // Last 4 digits only
    val cardBrand: CardBrand?,                     // Visa, MasterCard, etc.
    val cardHolderName: String?,
    val expirationMonth: Int?,
    val expirationYear: Int?
)

@Serializable
enum class PaymentMethodType {
    CREDIT_CARD,
    DEBIT_CARD,
    DIGITAL_WALLET,                                // Apple Pay, Google Pay (future)
    BANK_TRANSFER                                  // Future use
}

@Serializable
enum class CardBrand {
    VISA,
    MASTERCARD,
    AMEX,
    DISCOVER,
    UNKNOWN
}

@Serializable
enum class PaymentFailureCode {
    INSUFFICIENT_FUNDS,
    CARD_DECLINED,
    CARD_EXPIRED,
    INVALID_CARD,
    FRAUD_SUSPECTED,
    GATEWAY_ERROR,
    TIMEOUT,
    UNKNOWN
}

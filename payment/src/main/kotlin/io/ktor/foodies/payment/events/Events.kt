package io.ktor.foodies.payment.events

import io.ktor.foodies.payment.PaymentFailureCode
import io.ktor.foodies.payment.PaymentMethodInfo
import io.ktor.foodies.server.SerializableBigDecimal
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class OrderStockConfirmedEvent(
    val eventId: String,                           // Idempotency key
    val orderId: Long,
    val buyerId: String,
    val totalAmount: SerializableBigDecimal,
    val currency: String,
    val paymentMethod: PaymentMethodInfo,
    val occurredAt: Instant
)

@Serializable
data class OrderPaymentSucceededEvent(
    val eventId: String,
    val orderId: Long,
    val paymentId: Long,
    val transactionId: String,
    val amount: SerializableBigDecimal,
    val currency: String,
    val processedAt: Instant
)

@Serializable
data class OrderPaymentFailedEvent(
    val eventId: String,
    val orderId: Long,
    val failureReason: String,
    val failureCode: PaymentFailureCode,
    val occurredAt: Instant
)

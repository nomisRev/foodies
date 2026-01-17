package io.ktor.foodies.events.payment

import io.ktor.foodies.events.common.PaymentFailureCode
import io.ktor.foodies.rabbitmq.HasRoutingKey
import io.ktor.foodies.server.SerializableBigDecimal
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class OrderPaymentSucceededEvent(
    val eventId: String,
    val orderId: Long,
    val paymentId: Long,
    val transactionId: String,
    val amount: SerializableBigDecimal,
    val currency: String,
    val processedAt: Instant
) : HasRoutingKey {
    override val key: String = "payment.succeeded"
}

@Serializable
data class OrderPaymentFailedEvent(
    val eventId: String,
    val orderId: Long,
    val failureReason: String,
    val failureCode: PaymentFailureCode,
    val occurredAt: Instant
) : HasRoutingKey {
    override val key: String = "payment.failed"
}

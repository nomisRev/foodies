package io.ktor.foodies.payment.events

import io.ktor.foodies.events.common.PaymentFailureCode
import io.ktor.foodies.events.common.PaymentMethodInfo
import io.ktor.foodies.rabbitmq.HasRoutingKey
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
) : HasRoutingKey {
    override val key: String = "order.stock-confirmed"
}


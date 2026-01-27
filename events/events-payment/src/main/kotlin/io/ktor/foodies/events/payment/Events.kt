package io.ktor.foodies.events.payment

import io.ktor.foodies.events.common.PaymentFailureCode
import io.ktor.foodies.rabbitmq.RoutingKey
import io.ktor.foodies.rabbitmq.HasRoutingKey
import io.ktor.foodies.server.SerializableBigDecimal
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class OrderPaymentSucceededEvent(
    val eventId: String,
    val orderId: Long,
    val paymentId: Long,
    val transactionId: String,
    val amount: SerializableBigDecimal,
    val currency: String,
    val processedAt: Instant
) : HasRoutingKey<OrderPaymentSucceededEvent> {
    @Transient
    override val routingKey: RoutingKey<OrderPaymentSucceededEvent> = key()

    companion object {
        fun key(): RoutingKey<OrderPaymentSucceededEvent> = RoutingKey("payment.succeeded", serializer())
    }
}

@Serializable
data class OrderPaymentFailedEvent(
    val eventId: String,
    val orderId: Long,
    val failureReason: String,
    val failureCode: PaymentFailureCode,
    val occurredAt: Instant
) : HasRoutingKey<OrderPaymentFailedEvent> {
    @Transient
    override val routingKey: RoutingKey<OrderPaymentFailedEvent> = key()

    companion object {
        fun key(): RoutingKey<OrderPaymentFailedEvent> = RoutingKey("payment.failed", serializer())
    }
}

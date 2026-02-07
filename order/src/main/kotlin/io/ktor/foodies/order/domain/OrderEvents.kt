package io.ktor.foodies.order.domain

import io.ktor.foodies.rabbitmq.HasRoutingKey
import io.ktor.foodies.rabbitmq.RoutingKey
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class GracePeriodExpiredEvent(val orderId: Long, val expiredAt: Instant) :
    HasRoutingKey<GracePeriodExpiredEvent> {

    @kotlinx.serialization.Transient
    override val routingKey: RoutingKey<GracePeriodExpiredEvent> = key()

    companion object {
        fun key(): RoutingKey<GracePeriodExpiredEvent> =
            RoutingKey("order.grace-period.expired", serializer())
    }
}

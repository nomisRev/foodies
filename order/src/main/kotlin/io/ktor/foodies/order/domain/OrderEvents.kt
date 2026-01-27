package io.ktor.foodies.order.domain

import io.ktor.foodies.rabbitmq.HasRoutingKey
import io.ktor.foodies.rabbitmq.RoutingKey
import io.ktor.foodies.rabbitmq.RoutingKeyOwner
import io.ktor.server.routing.Routing
import kotlinx.serialization.KSerializer
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class GracePeriodExpiredEvent(
    val orderId: Long,
    val expiredAt: Instant
) : RoutingKeyOwner<GracePeriodExpiredEvent> {

    @kotlinx.serialization.Transient
    override val routingKey: RoutingKey<GracePeriodExpiredEvent> = key()

    companion object {
        fun key(): RoutingKey<GracePeriodExpiredEvent> = RoutingKey("order.grace-period.expired", serializer())
    }
}

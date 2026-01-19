package io.ktor.foodies.order.domain

import io.ktor.foodies.rabbitmq.HasRoutingKey
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class GracePeriodExpiredEvent(val orderId: Long, val expiredAt: Instant) : HasRoutingKey {
    override val key: String = "order.grace-period.expired"
}

package io.ktor.foodies.order.placement

import com.rabbitmq.client.AMQP
import io.ktor.foodies.events.order.OrderCreatedEvent
import io.ktor.foodies.rabbitmq.Publisher
import kotlin.time.Duration

interface PlacementEventPublisher {
    suspend fun publish(event: OrderCreatedEvent)
    suspend fun publish(event: GracePeriodExpiredEvent, delay: Duration)
}

class RabbitPlacementEventPublisher(
    private val publisher: Publisher,
) : PlacementEventPublisher {
    override suspend fun publish(event: OrderCreatedEvent) = publisher.publish(event)

    override suspend fun publish(event: GracePeriodExpiredEvent, delay: Duration) {
        val props = AMQP.BasicProperties.Builder()
            .headers(mapOf("x-delay" to delay.inWholeMilliseconds))
            .build()
        publisher.publish(event, props)
    }
}

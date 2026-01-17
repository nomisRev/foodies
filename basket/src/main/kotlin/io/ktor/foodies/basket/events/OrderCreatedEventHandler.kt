package io.ktor.foodies.basket.events

import io.ktor.foodies.basket.BasketRepository
import io.ktor.foodies.rabbitmq.Message
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("OrderCreatedEventHandler")

/**
 * Creates a consumer that handles OrderCreatedEvent messages.
 * When an order is created, the basket for the buyer is cleared.
 */
fun orderCreatedEventConsumer(
    orderEvents: Flow<Message<OrderCreatedEvent>>,
    basketRepository: BasketRepository
): Flow<Unit> = orderEvents.map { message ->
    try {
        val event = message.value
        basketRepository.deleteBasket(event.buyerId)
        logger.info("Cleared basket for user ${event.buyerId} after order ${event.orderId}")
        message.ack()
    } catch (e: Exception) {
        logger.error("Failed to process OrderCreatedEvent", e)
        message.nack()
    }
}.retry { e ->
    delay(1000) // TODO: Introduce proper resilience schedule
    logger.error("Failed to process OrderCreatedEvent, retrying", e)
    true // Retry and continue processing forever
}

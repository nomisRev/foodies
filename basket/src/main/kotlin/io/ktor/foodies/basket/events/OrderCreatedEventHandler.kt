package io.ktor.foodies.basket.events

import io.ktor.foodies.basket.BasketRepository
import io.ktor.foodies.rabbitmq.Message
import io.ktor.foodies.rabbitmq.parConsumeMessage
import kotlinx.coroutines.flow.Flow
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("OrderCreatedEventHandler")

/**
 * Creates a consumer that handles OrderCreatedEvent messages.
 * When an order is created, the basket for the buyer is cleared.
 */
fun orderCreatedEventConsumer(
    orderEvents: Flow<Message<OrderCreatedEvent>>,
    basketRepository: BasketRepository
) = orderEvents.parConsumeMessage { event ->
    basketRepository.deleteBasket(event.buyerId)
    logger.info("Cleared basket for user ${event.buyerId} after order ${event.orderId}")
}

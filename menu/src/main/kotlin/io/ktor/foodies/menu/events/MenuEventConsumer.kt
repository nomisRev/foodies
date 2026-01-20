package io.ktor.foodies.menu.events

import io.ktor.foodies.events.menu.StockConfirmedEvent
import io.ktor.foodies.events.menu.StockRejectedEvent
import io.ktor.foodies.events.order.*
import io.ktor.foodies.menu.MenuService
import io.ktor.foodies.menu.StockValidationResult
import io.ktor.foodies.rabbitmq.RabbitMQSubscriber
import io.ktor.foodies.rabbitmq.parConsumeMessage
import io.ktor.foodies.rabbitmq.subscribe
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(RabbitMQSubscriber::class.java)

fun orderAwaitingValidationConsumer(
    subscriber: RabbitMQSubscriber,
    queueName: String,
    menuService: MenuService,
    eventPublisher: MenuEventPublisher
) = subscriber.subscribe<OrderAwaitingValidationEvent>(queueName) { exchange ->
    queueDeclare(queueName, true, false, false, null)
    queueBind(queueName, exchange, "order.awaiting-validation")
}.parConsumeMessage { event ->
    logger.info("Processing OrderAwaitingValidationEvent for order ${event.orderId}")
    when (val result = menuService.validateAndReserveStock(event.orderId, event.items)) {
        is StockValidationResult.Success -> {
            eventPublisher.publish(StockConfirmedEvent(event.orderId, result.confirmedAt))
            logger.info("Stock confirmed for order ${event.orderId}")
        }

        is StockValidationResult.Failure -> {
            eventPublisher.publish(
                StockRejectedEvent(
                    event.orderId,
                    result.rejectedItems,
                    result.rejectedAt
                )
            )
            logger.info("Stock rejected for order ${event.orderId}")
        }
    }
}

fun stockReturnedConsumer(
    subscriber: RabbitMQSubscriber,
    queueName: String,
    menuService: MenuService
) = subscriber.subscribe<StockReturnedEvent>(queueName) { exchange ->
    queueDeclare(queueName, true, false, false, null)
    queueBind(queueName, exchange, "order.stock-returned")
}.parConsumeMessage { event ->
    logger.info("Processing StockReturnedEvent for order ${event.orderId}")
    menuService.returnStock(event.orderId, event.items)
}

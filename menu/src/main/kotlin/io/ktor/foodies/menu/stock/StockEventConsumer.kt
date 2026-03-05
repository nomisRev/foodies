package io.ktor.foodies.menu.stock

import io.ktor.foodies.events.menu.StockConfirmedEvent
import io.ktor.foodies.events.menu.StockRejectedEvent
import io.ktor.foodies.events.order.OrderAwaitingValidationEvent
import io.ktor.foodies.events.order.StockReturnedEvent
import io.ktor.foodies.rabbitmq.RabbitMQSubscriber
import io.ktor.foodies.rabbitmq.parConsumeMessage
import io.ktor.foodies.rabbitmq.subscribe
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(RabbitMQSubscriber::class.java)

fun orderAwaitingValidationConsumer(
    subscriber: RabbitMQSubscriber,
    queueName: String,
    stockService: StockService,
    eventPublisher: StockEventPublisher
) = subscriber.subscribe(OrderAwaitingValidationEvent.key(), queueName) { exchange ->
    queueDeclare(queueName, true, false, false, null)
    queueBind(queueName, exchange, "order.awaiting-validation")
}.parConsumeMessage { event ->
    logger.info("Processing OrderAwaitingValidationEvent for order ${event.orderId}")
    when (val result = stockService.validateAndReserveStock(event.orderId, event.items)) {
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
    stockService: StockService
) = subscriber.subscribe(StockReturnedEvent.key(), queueName) { exchange ->
    queueDeclare(queueName, true, false, false, null)
    queueBind(queueName, exchange, "order.stock-returned")
}.parConsumeMessage { event ->
    logger.info("Processing StockReturnedEvent for order ${event.orderId}")
    stockService.returnStock(event.orderId, event.items)
}

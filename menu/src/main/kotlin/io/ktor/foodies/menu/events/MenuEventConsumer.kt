package io.ktor.foodies.menu.events

import io.ktor.foodies.menu.MenuService
import io.ktor.foodies.menu.StockValidationResult
import io.ktor.foodies.rabbitmq.RabbitMQSubscriber
import io.ktor.foodies.rabbitmq.consumeMessage
import io.ktor.foodies.rabbitmq.subscribe
import kotlinx.coroutines.flow.Flow
import org.slf4j.LoggerFactory
import kotlin.jvm.java

private val logger = LoggerFactory.getLogger(RabbitMQSubscriber::class.java)

fun RabbitMQSubscriber.processOrderAwaitingValidationConsumer(
    queueName: String,
    menuService: MenuService,
    eventPublisher: MenuEventPublisher
) = subscribe<OrderAwaitingValidationEvent>(queueName) { exchange ->
    queueDeclare(queueName, true, false, false, null)
    queueBind(queueName, exchange, "order.awaiting-validation")
}.consumeMessage { event ->
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

fun RabbitMQSubscriber.processStockReturnedConsumer(queueName: String, menuService: MenuService): Flow<Unit> =
    subscribe<OrderAwaitingValidationEvent>(queueName) { exchange ->
    queueDeclare(queueName, true, false, false, null)
    queueBind(queueName, exchange, "order.stock-returned")
}.consumeMessage { event ->
    logger.info("Processing StockReturnedEvent for order ${event.orderId}")
    menuService.returnStock(event.orderId, event.items)
}

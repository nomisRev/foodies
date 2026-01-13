package io.ktor.foodies.menu.events

import com.rabbitmq.client.Channel
import io.ktor.foodies.menu.MenuService
import io.ktor.foodies.menu.StockValidationResult
import io.ktor.foodies.rabbitmq.messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.slf4j.LoggerFactory

class MenuEventConsumer(
    private val channel: Channel,
    private val exchange: String,
    private val queueName: String,
    private val menuService: MenuService,
    private val eventPublisher: MenuEventPublisher,
    private val scope: CoroutineScope
) {
    private val logger = LoggerFactory.getLogger(MenuEventConsumer::class.java)

    fun start() {
        setupOrderAwaitingValidationConsumer()
        setupStockReturnedConsumer()
    }

    private fun setupOrderAwaitingValidationConsumer() {
        val routingKey = "order.awaiting-validation"
        
        channel.queueDeclare(queueName, true, false, false, null)
        channel.queueBind(queueName, exchange, routingKey)

        channel.messages<OrderAwaitingValidationEvent>(queueName)
            .onEach { message ->
                runCatching {
                    val event = message.value
                    logger.info("Processing OrderAwaitingValidationEvent for order ${event.orderId}")
                    
                    val result = menuService.validateAndReserveStock(event.orderId, event.items)
                    
                    when (result) {
                        is StockValidationResult.Success -> {
                            eventPublisher.publish(StockConfirmedEvent(event.orderId, result.confirmedAt))
                            logger.info("Stock confirmed for order ${event.orderId}")
                        }
                        is StockValidationResult.Failure -> {
                            eventPublisher.publish(StockRejectedEvent(event.orderId, result.rejectedItems, result.rejectedAt))
                            logger.info("Stock rejected for order ${event.orderId}")
                        }
                    }
                    message.ack()
                }.onFailure { e ->
                    logger.error("Error processing OrderAwaitingValidationEvent", e)
                    message.nack()
                }
            }
            .launchIn(scope)
    }

    private fun setupStockReturnedConsumer() {
        val queueName = "menu.stock-returned"
        val routingKey = "order.stock-returned"

        channel.queueDeclare(queueName, true, false, false, null)
        channel.queueBind(queueName, exchange, routingKey)

        channel.messages<StockReturnedEvent>(queueName)
            .onEach { message ->
                runCatching {
                    val event = message.value
                    logger.info("Processing StockReturnedEvent for order ${event.orderId}")
                    menuService.returnStock(event.orderId, event.items)
                    message.ack()
                }.onFailure { e ->
                    logger.error("Error processing StockReturnedEvent", e)
                    message.nack()
                }
            }
            .launchIn(scope)
    }
}

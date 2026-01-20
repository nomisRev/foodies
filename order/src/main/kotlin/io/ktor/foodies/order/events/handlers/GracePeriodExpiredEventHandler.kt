package io.ktor.foodies.order.events.handlers

import io.ktor.foodies.order.domain.GracePeriodExpiredEvent
import io.ktor.foodies.order.service.OrderService
import org.slf4j.LoggerFactory

class GracePeriodExpiredEventHandler(
    private val orderService: OrderService
) {
    private val logger = LoggerFactory.getLogger(GracePeriodExpiredEventHandler::class.java)

    suspend fun handle(event: GracePeriodExpiredEvent) {
        logger.info("Grace period expired for order ${event.orderId}")
        orderService.transitionToAwaitingValidation(event.orderId)
    }
}

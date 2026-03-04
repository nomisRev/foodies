package io.ktor.foodies.order.fulfillment.handlers

import io.ktor.foodies.order.fulfillment.FulfillmentService
import io.ktor.foodies.order.placement.GracePeriodExpiredEvent
import org.slf4j.LoggerFactory

class GracePeriodExpiredHandler(
    private val fulfillmentService: FulfillmentService
) {
    private val logger = LoggerFactory.getLogger(GracePeriodExpiredHandler::class.java)

    suspend fun handle(event: GracePeriodExpiredEvent) {
        logger.info("Grace period expired for order ${event.orderId}")
        fulfillmentService.transitionToAwaitingValidation(event.orderId)
    }
}

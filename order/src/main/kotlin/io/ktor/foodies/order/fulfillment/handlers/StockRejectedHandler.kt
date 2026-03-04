package io.ktor.foodies.order.fulfillment.handlers

import io.ktor.foodies.events.menu.StockRejectedEvent
import io.ktor.foodies.order.fulfillment.FulfillmentService

class StockRejectedHandler(
    private val fulfillmentService: FulfillmentService
) {
    suspend fun handle(event: StockRejectedEvent) {
        fulfillmentService.processStockRejection(event.orderId, event.rejectedItems)
    }
}

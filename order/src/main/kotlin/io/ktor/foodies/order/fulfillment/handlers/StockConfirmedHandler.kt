package io.ktor.foodies.order.fulfillment.handlers

import io.ktor.foodies.events.menu.StockConfirmedEvent
import io.ktor.foodies.order.fulfillment.FulfillmentService

class StockConfirmedHandler(
    private val fulfillmentService: FulfillmentService
) {
    suspend fun handle(event: StockConfirmedEvent) {
        fulfillmentService.setStockConfirmed(event.orderId)
    }
}

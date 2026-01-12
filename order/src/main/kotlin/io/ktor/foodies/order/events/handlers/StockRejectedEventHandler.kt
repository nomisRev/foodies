package io.ktor.foodies.order.events.handlers

import io.ktor.foodies.order.domain.StockRejectedEvent
import io.ktor.foodies.order.service.OrderService

class StockRejectedEventHandler(
    private val orderService: OrderService
) {
    suspend fun handle(event: StockRejectedEvent) {
        val reason = "Stock rejected for items: " + event.rejectedItems.joinToString(", ") { 
            "${it.menuItemName} (Requested: ${it.requestedQuantity}, Available: ${it.availableQuantity})" 
        }
        orderService.cancelOrderDueToStockRejection(event.orderId, reason)
    }
}

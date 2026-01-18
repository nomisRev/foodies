package io.ktor.foodies.order.events.handlers

import io.ktor.foodies.events.menu.StockRejectedEvent
import io.ktor.foodies.order.service.OrderService

class StockRejectedEventHandler(
    private val orderService: OrderService
) {
    suspend fun handle(event: StockRejectedEvent) {
        orderService.processStockRejection(event.orderId, event.rejectedItems)
    }
}

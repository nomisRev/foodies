package io.ktor.foodies.order.events.handlers

import io.ktor.foodies.events.menu.StockConfirmedEvent
import io.ktor.foodies.order.service.OrderService

class StockConfirmedEventHandler(private val orderService: OrderService) {
    suspend fun handle(event: StockConfirmedEvent) {
        orderService.setStockConfirmed(event.orderId)
    }
}

package io.ktor.foodies.order.events.handlers

import io.ktor.foodies.order.domain.PaymentSucceededEvent
import io.ktor.foodies.order.service.OrderService

class PaymentSucceededEventHandler(
    private val orderService: OrderService
) {
    suspend fun handle(event: PaymentSucceededEvent) {
        orderService.setPaid(event.orderId)
    }
}

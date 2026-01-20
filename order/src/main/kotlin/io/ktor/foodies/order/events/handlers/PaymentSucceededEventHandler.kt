package io.ktor.foodies.order.events.handlers

import io.ktor.foodies.events.payment.OrderPaymentSucceededEvent
import io.ktor.foodies.order.service.OrderService

class PaymentSucceededEventHandler(
    private val orderService: OrderService
) {
    suspend fun handle(event: OrderPaymentSucceededEvent) {
        orderService.setPaid(event.orderId)
    }
}

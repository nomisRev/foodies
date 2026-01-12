package io.ktor.foodies.order.events.handlers

import io.ktor.foodies.order.domain.PaymentFailedEvent
import io.ktor.foodies.order.service.OrderService

class PaymentFailedEventHandler(
    private val orderService: OrderService
) {
    suspend fun handle(event: PaymentFailedEvent) {
        orderService.cancelOrderDueToPaymentFailure(event.orderId, event.reason)
    }
}

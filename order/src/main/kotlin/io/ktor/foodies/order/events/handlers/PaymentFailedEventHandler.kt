package io.ktor.foodies.order.events.handlers

import io.ktor.foodies.events.payment.OrderPaymentFailedEvent
import io.ktor.foodies.order.service.OrderService

class PaymentFailedEventHandler(
    private val orderService: OrderService
) {
    suspend fun handle(event: OrderPaymentFailedEvent) {
        orderService.cancelOrderDueToPaymentFailure(event.orderId, event.failureReason, event.failureCode)
    }
}

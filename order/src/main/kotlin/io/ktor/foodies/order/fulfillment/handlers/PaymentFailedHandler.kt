package io.ktor.foodies.order.fulfillment.handlers

import io.ktor.foodies.events.payment.OrderPaymentFailedEvent
import io.ktor.foodies.order.fulfillment.FulfillmentService

class PaymentFailedHandler(
    private val fulfillmentService: FulfillmentService
) {
    suspend fun handle(event: OrderPaymentFailedEvent) {
        fulfillmentService.cancelOrderDueToPaymentFailure(event.orderId, event.failureReason, event.failureCode)
    }
}

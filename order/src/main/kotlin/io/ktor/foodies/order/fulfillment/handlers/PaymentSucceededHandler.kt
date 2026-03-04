package io.ktor.foodies.order.fulfillment.handlers

import io.ktor.foodies.events.payment.OrderPaymentSucceededEvent
import io.ktor.foodies.order.fulfillment.FulfillmentService

class PaymentSucceededHandler(
    private val fulfillmentService: FulfillmentService
) {
    suspend fun handle(event: OrderPaymentSucceededEvent) {
        fulfillmentService.setPaid(event.orderId)
    }
}

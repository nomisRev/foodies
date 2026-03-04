package io.ktor.foodies.order.fulfillment.handlers

import io.ktor.foodies.events.order.OrderStatusChangedEvent
import io.ktor.foodies.order.fulfillment.FulfillmentNotificationService
import io.ktor.foodies.order.persistence.OrderRepository

class StatusChangedHandler(
    private val orderRepository: OrderRepository,
    private val notificationService: FulfillmentNotificationService
) {
    suspend fun handle(event: OrderStatusChangedEvent) {
        val order = orderRepository.findById(event.orderId) ?: return
        notificationService.notifyStatusChange(order, event.oldStatus)
    }
}

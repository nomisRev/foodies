package io.ktor.foodies.order.events.handlers

import io.ktor.foodies.order.domain.OrderStatusChangedEvent
import io.ktor.foodies.order.repository.OrderRepository
import io.ktor.foodies.order.service.NotificationService

class OrderStatusChangedEventHandler(
    private val orderRepository: OrderRepository,
    private val notificationService: NotificationService
) {
    suspend fun handle(event: OrderStatusChangedEvent) {
        val order = orderRepository.findById(event.orderId) ?: return
        notificationService.notifyStatusChange(order, event.oldStatus)
    }
}

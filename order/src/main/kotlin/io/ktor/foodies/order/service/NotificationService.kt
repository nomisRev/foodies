package io.ktor.foodies.order.service

import io.ktor.foodies.order.domain.Order
import io.ktor.foodies.events.order.OrderStatus
import org.slf4j.LoggerFactory

interface NotificationService {
    suspend fun notifyStatusChange(order: Order, oldStatus: OrderStatus)
}

class LoggingNotificationService : NotificationService {
    private val logger = LoggerFactory.getLogger(LoggingNotificationService::class.java)

    override suspend fun notifyStatusChange(order: Order, oldStatus: OrderStatus) {
        val message = "Notification: Order ${order.id} for ${order.buyerName} changed from $oldStatus to ${order.status}. Description: ${order.description ?: "None"}"
        logger.info(message)
        // TODO send an email or a push notification
        println("Sending Email to ${order.buyerEmail}: $message")
    }
}

package io.ktor.foodies.order.fulfillment

import io.ktor.foodies.events.order.OrderStatus
import io.ktor.foodies.order.Order
import org.slf4j.LoggerFactory

interface FulfillmentNotificationService {
    suspend fun notifyStatusChange(order: Order, oldStatus: OrderStatus)
}

class LoggingFulfillmentNotificationService : FulfillmentNotificationService {
    private val logger = LoggerFactory.getLogger(LoggingFulfillmentNotificationService::class.java)

    override suspend fun notifyStatusChange(order: Order, oldStatus: OrderStatus) {
        val message = "Notification: Order ${order.id} for ${order.buyerName} changed from $oldStatus to ${order.status}. Description: ${order.description ?: "None"}"
        logger.info(message)
        println("Sending Email to ${order.buyerEmail}: $message")
    }
}

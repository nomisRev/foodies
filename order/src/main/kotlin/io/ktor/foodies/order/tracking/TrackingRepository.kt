package io.ktor.foodies.order.tracking

import io.ktor.foodies.events.order.OrderStatus
import io.ktor.foodies.order.persistence.OrderRepository

interface TrackingRepository {
    fun findByBuyerId(buyerId: String, offset: Long, limit: Int, status: OrderStatus? = null): PaginatedOrders
    fun findAll(offset: Long, limit: Int, status: OrderStatus? = null, buyerId: String? = null): PaginatedOrders
}

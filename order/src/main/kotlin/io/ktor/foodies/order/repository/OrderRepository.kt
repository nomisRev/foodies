package io.ktor.foodies.order.repository

import io.ktor.foodies.events.order.OrderStatus
import io.ktor.foodies.order.domain.CreateOrder
import io.ktor.foodies.order.domain.Order
import io.ktor.foodies.order.domain.PaginatedOrders

interface OrderRepository {
    fun findById(id: Long): Order?

    fun findByRequestId(requestId: String): Order?

    fun findByBuyerId(
        buyerId: String,
        offset: Long,
        limit: Int,
        status: OrderStatus? = null,
    ): PaginatedOrders

    fun findAll(
        offset: Long,
        limit: Int,
        status: OrderStatus? = null,
        buyerId: String? = null,
    ): PaginatedOrders

    fun create(order: CreateOrder): Order

    fun update(order: Order): Order
}

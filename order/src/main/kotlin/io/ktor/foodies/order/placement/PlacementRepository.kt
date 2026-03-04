package io.ktor.foodies.order.placement

import io.ktor.foodies.order.Order

interface PlacementRepository {
    fun create(createOrder: CreateOrder): Order
    fun findByRequestId(requestId: String): Order?
}

package io.ktor.foodies.order.persistence

import io.ktor.foodies.order.Order
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

interface OrderRepository {
    fun findById(id: Long): Order?
    fun update(order: Order): Order
}

class ExposedOrderRepository(private val database: Database) : OrderRepository {

    override fun findById(id: Long): Order? = transaction(database) {
        Orders.findById(id)
    }

    override fun update(order: Order): Order = transaction(database) {
        Orders.update({ Orders.id eq order.id }) {
            it[status] = order.status
            it[totalPrice] = order.totalPrice
            it[description] = order.description
            it[updatedAt] = order.updatedAt
        }

        val currentItemIds = order.items.map { it.id }
        OrderItems.deleteWhere {
            (OrderItems.orderId eq order.id) and (OrderItems.id notInList currentItemIds)
        }
        order.items.forEach { item ->
            OrderItems.update({ OrderItems.id eq item.id }) {
                it[quantity] = item.quantity
                it[unitPrice] = item.unitPrice
                it[discount] = item.discount
            }
        }

        OrderHistory.insert {
            it[orderId] = order.id
            it[status] = order.status
            it[description] = order.description
        }

        Orders.findById(order.id)!!
    }
}

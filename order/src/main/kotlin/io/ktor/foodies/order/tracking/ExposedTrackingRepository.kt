package io.ktor.foodies.order.tracking

import io.ktor.foodies.events.order.OrderStatus
import io.ktor.foodies.order.Order
import io.ktor.foodies.order.persistence.OrderItems
import io.ktor.foodies.order.persistence.OrderRepository
import io.ktor.foodies.order.persistence.Orders
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ExposedTrackingRepository(private val database: Database) : TrackingRepository {

    override fun findByBuyerId(buyerId: String, offset: Long, limit: Int, status: OrderStatus?): PaginatedOrders =
        findAll(offset, limit, status, buyerId)

    override fun findAll(offset: Long, limit: Int, status: OrderStatus?, buyerId: String?): PaginatedOrders =
        transaction(database) {
            val query = Orders.selectAll()
            if (buyerId != null) query.andWhere { Orders.buyerId eq buyerId }
            if (status != null) query.andWhere { Orders.status eq status }

            val total = query.count()
            val summaries = query
                .orderBy(Orders.createdAt, SortOrder.DESC)
                .limit(limit)
                .offset(offset)
                .map { row ->
                    val orderId = row[Orders.id].value
                    val itemCount = OrderItems.selectAll()
                        .where { OrderItems.orderId eq orderId }
                        .sumOf { it[OrderItems.quantity] }
                    OrderSummary(
                        id = orderId,
                        status = row[Orders.status],
                        totalPrice = row[Orders.totalPrice],
                        itemCount = itemCount,
                        description = row[Orders.description],
                        createdAt = row[Orders.createdAt],
                    )
                }
            PaginatedOrders(summaries, total, offset, limit)
        }
}

package io.ktor.foodies.order.repository

import io.ktor.foodies.events.common.CardBrand
import io.ktor.foodies.order.database.OrderHistory
import io.ktor.foodies.order.database.OrderItems
import io.ktor.foodies.order.database.Orders
import io.ktor.foodies.order.database.PaymentMethods
import io.ktor.foodies.events.order.*
import io.ktor.foodies.order.domain.*
import io.ktor.foodies.server.SerializableBigDecimal
import java.math.BigDecimal
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class ExposedOrderRepository(private val database: Database) : OrderRepository {
    override fun findById(id: Long): Order? = transaction(database) {
        val orderRow = Orders.selectAll().where { Orders.id eq id }.singleOrNull() ?: return@transaction null
        val items = OrderItems.selectAll().where { OrderItems.orderId eq id }.map { it.toOrderItem() }
        val history = OrderHistory.selectAll().where { OrderHistory.orderId eq id }.orderBy(OrderHistory.createdAt, SortOrder.ASC).map { it.toOrderHistoryEntry() }
        val paymentMethodId = orderRow[Orders.paymentMethodId]
        val paymentMethod = paymentMethodId?.let { pmId ->
            PaymentMethods.selectAll().where { PaymentMethods.id eq pmId }.map { it.toPaymentMethod() }.singleOrNull()
        }

        orderRow.toOrder(items, paymentMethod, history)
    }

    override fun findByRequestId(requestId: String): Order? = transaction(database) {
        val orderRow = Orders.selectAll().where { Orders.requestId eq requestId }.singleOrNull() ?: return@transaction null
        val orderId = orderRow[Orders.id].value
        val items = OrderItems.selectAll().where { OrderItems.orderId eq orderId }.map { it.toOrderItem() }
        val history = OrderHistory.selectAll().where { OrderHistory.orderId eq orderId }.orderBy(OrderHistory.createdAt, SortOrder.ASC).map { it.toOrderHistoryEntry() }
        val paymentMethodId = orderRow[Orders.paymentMethodId]
        val paymentMethod = paymentMethodId?.let { pmId ->
            PaymentMethods.selectAll().where { PaymentMethods.id eq pmId }.map { it.toPaymentMethod() }.singleOrNull()
        }

        orderRow.toOrder(items, paymentMethod, history)
    }

    override fun findByBuyerId(
        buyerId: String,
        offset: Long,
        limit: Int,
        status: OrderStatus?
    ): PaginatedOrders = findAll(offset, limit, status, buyerId)

    override fun findAll(
        offset: Long,
        limit: Int,
        status: OrderStatus?,
        buyerId: String?
    ): PaginatedOrders = transaction(database) {
        val query = Orders.selectAll()
        
        if (buyerId != null) {
            query.andWhere { Orders.buyerId eq buyerId }
        }
        
        if (status != null) {
            query.andWhere { Orders.status eq status }
        }

        val total = query.count()
        val orders = query
            .orderBy(Orders.createdAt, SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map { row ->
                val orderId = row[Orders.id].value
                val itemCount = OrderItems.selectAll().where { OrderItems.orderId eq orderId }.sumOf { it[OrderItems.quantity] }
                OrderSummary(
                    id = orderId,
                    status = row[Orders.status],
                    totalPrice = row[Orders.totalPrice],
                    itemCount = itemCount,
                    description = row[Orders.description],
                    createdAt = row[Orders.createdAt]
                )
            }

        PaginatedOrders(orders, total, offset, limit)
    }

    override fun create(order: CreateOrder): Order = transaction(database) {
        val maskedNumber = order.paymentDetails.cardNumber.takeLast(4)

        // Find or create payment method
        val paymentMethodId = PaymentMethods.selectAll()
            .where { (PaymentMethods.buyerId eq order.buyerId) and (PaymentMethods.cardType eq order.paymentDetails.cardType) and (PaymentMethods.cardNumberMasked eq maskedNumber) }
            .singleOrNull()?.get(PaymentMethods.id)
            ?: PaymentMethods.insertReturning(listOf(PaymentMethods.id)) {
                it[buyerId] = order.buyerId
                it[cardType] = order.paymentDetails.cardType
                it[cardHolderName] = order.paymentDetails.cardHolderName
                it[cardNumberMasked] = maskedNumber
                it[expirationMonth] = order.paymentDetails.expirationMonth
                it[expirationYear] = order.paymentDetails.expirationYear
            }.single()[PaymentMethods.id]

        val returning = Orders.insertReturning(
            listOf(Orders.id, Orders.createdAt, Orders.updatedAt)
        ) {
            it[requestId] = order.requestId
            it[buyerId] = order.buyerId
            it[buyerEmail] = order.buyerEmail
            it[buyerName] = order.buyerName
            it[status] = OrderStatus.Submitted
            it[totalPrice] = order.totalPrice
            it[currency] = order.currency
            it[description] = "Order submitted"
            it[street] = order.deliveryAddress.street
            it[city] = order.deliveryAddress.city
            it[state] = order.deliveryAddress.state
            it[country] = order.deliveryAddress.country
            it[zipCode] = order.deliveryAddress.zipCode
            it[Orders.paymentMethodId] = paymentMethodId
        }.single()
        val orderId = returning[Orders.id]

        OrderHistory.insert {
            it[OrderHistory.orderId] = orderId
            it[status] = OrderStatus.Submitted
            it[description] = "Order submitted"
        }

        order.items.forEach { item ->
            OrderItems.insert {
                it[OrderItems.orderId] = orderId
                it[menuItemId] = item.menuItemId
                it[menuItemName] = item.menuItemName
                it[pictureUrl] = item.pictureUrl
                it[unitPrice] = item.unitPrice
                it[quantity] = item.quantity
                it[discount] = BigDecimal.ZERO
            }
        }

        findById(orderId.value)!!
    }

    override fun update(order: Order): Order = transaction(database) {
        Orders.update({ Orders.id eq order.id }) {
            it[status] = order.status
            it[totalPrice] = order.totalPrice
            it[description] = order.description
            it[updatedAt] = order.updatedAt
        }

        // Handle OrderItems updates for partial fulfillment
        val currentItemIds = order.items.map { it.id }
        OrderItems.deleteWhere { (OrderItems.orderId eq order.id) and (OrderItems.id notInList currentItemIds) }

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
        findById(order.id)!!
    }

    private fun ResultRow.toOrder(items: List<OrderItem>, paymentMethod: PaymentMethod?, history: List<OrderHistoryEntry>) = Order(
        id = this[Orders.id].value,
        requestId = this[Orders.requestId],
        buyerId = this[Orders.buyerId],
        buyerEmail = this[Orders.buyerEmail],
        buyerName = this[Orders.buyerName],
        status = this[Orders.status],
        deliveryAddress = Address(
            street = this[Orders.street],
            city = this[Orders.city],
            state = this[Orders.state],
            country = this[Orders.country],
            zipCode = this[Orders.zipCode]
        ),
        items = items,
        paymentMethod = paymentMethod,
        totalPrice = this[Orders.totalPrice],
        currency = this[Orders.currency],
        description = this[Orders.description],
        history = history,
        createdAt = this[Orders.createdAt],
        updatedAt = this[Orders.updatedAt]
    )

    private fun ResultRow.toOrderHistoryEntry() = OrderHistoryEntry(
        id = this[OrderHistory.id].value,
        orderId = this[OrderHistory.orderId].value,
        status = this[OrderHistory.status],
        description = this[OrderHistory.description],
        createdAt = this[OrderHistory.createdAt]
    )

    private fun ResultRow.toOrderItem() = OrderItem(
        id = this[OrderItems.id].value,
        menuItemId = this[OrderItems.menuItemId],
        menuItemName = this[OrderItems.menuItemName],
        pictureUrl = this[OrderItems.pictureUrl],
        unitPrice = this[OrderItems.unitPrice],
        quantity = this[OrderItems.quantity],
        discount = this[OrderItems.discount]
    )

    private fun ResultRow.toPaymentMethod() = PaymentMethod(
        id = this[PaymentMethods.id].value,
        cardType = this[PaymentMethods.cardType],
        cardHolderName = this[PaymentMethods.cardHolderName],
        cardNumber = this[PaymentMethods.cardNumberMasked],
        expirationMonth = this[PaymentMethods.expirationMonth],
        expirationYear = this[PaymentMethods.expirationYear]
    )
}

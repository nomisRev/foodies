package io.ktor.foodies.order.persistence

import io.ktor.foodies.order.Address
import io.ktor.foodies.order.Order
import io.ktor.foodies.order.OrderHistoryEntry
import io.ktor.foodies.order.OrderItem
import io.ktor.foodies.order.PaymentMethod
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

internal fun Orders.findById(id: Long): Order? {
    val orderRow = selectAll().where { Orders.id eq id }.singleOrNull() ?: return null
    val items = OrderItems.selectAll().where { OrderItems.orderId eq id }.map { it.toOrderItem() }
    val history = OrderHistory.selectAll()
        .where { OrderHistory.orderId eq id }
        .orderBy(OrderHistory.createdAt, SortOrder.ASC)
        .map { it.toOrderHistoryEntry() }
    val paymentMethod = orderRow[Orders.paymentMethodId]?.let { pmId ->
        PaymentMethods.selectAll().where { PaymentMethods.id eq pmId }.map { it.toPaymentMethod() }.singleOrNull()
    }
    return orderRow.toOrder(items, paymentMethod, history)
}


internal fun ResultRow.toOrder(
    items: List<OrderItem>,
    paymentMethod: PaymentMethod?,
    history: List<OrderHistoryEntry>,
) = Order(
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
        zipCode = this[Orders.zipCode],
    ),
    items = items,
    paymentMethod = paymentMethod,
    totalPrice = this[Orders.totalPrice],
    currency = this[Orders.currency],
    description = this[Orders.description],
    history = history,
    createdAt = this[Orders.createdAt],
    updatedAt = this[Orders.updatedAt],
)

internal fun ResultRow.toOrderItem() = OrderItem(
    id = this[OrderItems.id].value,
    menuItemId = this[OrderItems.menuItemId],
    menuItemName = this[OrderItems.menuItemName],
    pictureUrl = this[OrderItems.pictureUrl],
    unitPrice = this[OrderItems.unitPrice],
    quantity = this[OrderItems.quantity],
    discount = this[OrderItems.discount],
)

internal fun ResultRow.toOrderHistoryEntry() = OrderHistoryEntry(
    id = this[OrderHistory.id].value,
    orderId = this[OrderHistory.orderId].value,
    status = this[OrderHistory.status],
    description = this[OrderHistory.description],
    createdAt = this[OrderHistory.createdAt],
)

internal fun ResultRow.toPaymentMethod() = PaymentMethod(
    id = this[PaymentMethods.id].value,
    cardType = this[PaymentMethods.cardType],
    cardHolderName = this[PaymentMethods.cardHolderName],
    cardNumber = this[PaymentMethods.cardNumberMasked],
    expirationMonth = this[PaymentMethods.expirationMonth],
    expirationYear = this[PaymentMethods.expirationYear],
)

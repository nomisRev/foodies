package io.ktor.foodies.order.placement

import io.ktor.foodies.events.order.OrderStatus
import io.ktor.foodies.order.Order
import io.ktor.foodies.order.persistence.OrderHistory
import io.ktor.foodies.order.persistence.OrderItems
import io.ktor.foodies.order.persistence.Orders
import io.ktor.foodies.order.persistence.PaymentMethods
import io.ktor.foodies.order.persistence.findById
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import java.math.BigDecimal
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ExposedPlacementRepository(private val database: Database) : PlacementRepository {

    override fun findByRequestId(requestId: String): Order? = transaction(database) {
        Orders.selectAll().where { Orders.requestId eq requestId }
            .singleOrNull()
            ?.let { Orders.findById(it[Orders.id].value) }
    }

    override fun create(createOrder: CreateOrder): Order = transaction(database) {
        val maskedNumber = createOrder.paymentDetails.cardNumber.takeLast(4)

        val paymentMethodId = PaymentMethods.selectAll()
            .where {
                (PaymentMethods.buyerId eq createOrder.buyerId) and
                        (PaymentMethods.cardType eq createOrder.paymentDetails.cardType) and
                        (PaymentMethods.cardNumberMasked eq maskedNumber)
            }
            .singleOrNull()?.get(PaymentMethods.id)
            ?: PaymentMethods.insertReturning(listOf(PaymentMethods.id)) {
                it[buyerId] = createOrder.buyerId
                it[cardType] = createOrder.paymentDetails.cardType
                it[cardHolderName] = createOrder.paymentDetails.cardHolderName
                it[cardNumberMasked] = maskedNumber
                it[expirationMonth] = createOrder.paymentDetails.expirationMonth
                it[expirationYear] = createOrder.paymentDetails.expirationYear
            }.single()[PaymentMethods.id]

        val returning = Orders.insertReturning(listOf(Orders.id, Orders.createdAt, Orders.updatedAt)) {
            it[requestId] = createOrder.requestId
            it[buyerId] = createOrder.buyerId
            it[buyerEmail] = createOrder.buyerEmail
            it[buyerName] = createOrder.buyerName
            it[status] = OrderStatus.Submitted
            it[totalPrice] = createOrder.totalPrice
            it[currency] = createOrder.currency
            it[description] = "Order submitted"
            it[street] = createOrder.deliveryAddress.street
            it[city] = createOrder.deliveryAddress.city
            it[state] = createOrder.deliveryAddress.state
            it[country] = createOrder.deliveryAddress.country
            it[zipCode] = createOrder.deliveryAddress.zipCode
            it[Orders.paymentMethodId] = paymentMethodId
        }.single()
        val orderId = returning[Orders.id]

        OrderHistory.insert {
            it[OrderHistory.orderId] = orderId
            it[status] = OrderStatus.Submitted
            it[description] = "Order submitted"
        }

        createOrder.items.forEach { item ->
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

        Orders.findById(orderId.value)!!
    }
}

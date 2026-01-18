package io.ktor.foodies.order.database

import io.ktor.foodies.events.common.CardBrand
import io.ktor.foodies.events.order.OrderStatus
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

object PaymentMethods : LongIdTable("payment_methods") {
    val buyerId = varchar("buyer_id", 255)
    val cardType = enumerationByName("card_type", 50, CardBrand::class)
    val cardHolderName = varchar("card_holder_name", 255)
    val cardNumberMasked = varchar("card_number_masked", 4)
    val expirationMonth = integer("expiration_month")
    val expirationYear = integer("expiration_year")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    init {
        uniqueIndex("payment_methods_unique", buyerId, cardType, cardNumberMasked)
    }
}

object Orders : LongIdTable("orders") {
    val requestId = varchar("request_id", 255).uniqueIndex()
    val buyerId = varchar("buyer_id", 255)
    val buyerEmail = varchar("buyer_email", 255)
    val buyerName = varchar("buyer_name", 255)
    val status = enumerationByName("status", 50, OrderStatus::class)
    val totalPrice = decimal("total_price", 19, 4)
    val currency = varchar("currency", 3).default("USD")
    val description = text("description").nullable()
    val street = varchar("street", 255)
    val city = varchar("city", 255)
    val state = varchar("state", 255)
    val country = varchar("country", 255)
    val zipCode = varchar("zip_code", 50)
    val paymentMethodId = reference("payment_method_id", PaymentMethods).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}

object OrderItems : LongIdTable("order_items") {
    val orderId = reference("order_id", Orders, onDelete = ReferenceOption.CASCADE)
    val menuItemId = long("menu_item_id")
    val menuItemName = varchar("menu_item_name", 255)
    val pictureUrl = text("picture_url")
    val unitPrice = decimal("unit_price", 19, 4)
    val quantity = integer("quantity")
    val discount = decimal("discount", 19, 4)
}

object OrderHistory : LongIdTable("order_history") {
    val orderId = reference("order_id", Orders, onDelete = ReferenceOption.CASCADE)
    val status = enumerationByName("status", 50, OrderStatus::class)
    val description = text("description").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

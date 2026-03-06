package io.ktor.foodies.payment.persistence

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

object PaymentsTable : LongIdTable("payments") {
    val orderId = long("order_id").uniqueIndex()
    val buyerId = varchar("buyer_id", 255)
    val amount = decimal("amount", 12, 2)
    val currency = varchar("currency", 3).default("USD")
    val status = varchar("status", 20).default("PENDING")
    val paymentMethodType = varchar("payment_method_type", 20)
    val cardLastFour = varchar("card_last_four", 4).nullable()
    val cardBrand = varchar("card_brand", 20).nullable()
    val cardHolderName = varchar("card_holder_name", 200).nullable()
    val expirationMonth = integer("expiration_month").nullable()
    val expirationYear = integer("expiration_year").nullable()
    val transactionId = varchar("transaction_id", 255).nullable()
    val failureReason = text("failure_reason").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val processedAt = timestamp("processed_at").nullable()
}

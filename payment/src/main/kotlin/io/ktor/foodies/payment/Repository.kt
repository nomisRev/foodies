package io.ktor.foodies.payment

import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal

interface PaymentRepository {
    fun create(payment: PaymentRecord): PaymentRecord
    fun findById(id: Long): PaymentRecord?
    fun findByOrderId(orderId: Long): PaymentRecord?
    fun findByBuyerId(buyerId: String, limit: Int = 50, offset: Int = 0): List<PaymentRecord>
    fun updateStatus(
        paymentId: Long,
        status: PaymentStatus,
        transactionId: String? = null,
        failureReason: String? = null,
        processedAt: Instant? = null
    ): Boolean
}

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

class PostgresPaymentRepository(
    private val database: Database
) : PaymentRepository {

    override fun create(payment: PaymentRecord): PaymentRecord = transaction(database) {
        val id = PaymentsTable.insertAndGetId {
            it[orderId] = payment.orderId
            it[buyerId] = payment.buyerId
            it[amount] = payment.amount
            it[currency] = payment.currency
            it[status] = payment.status.name
            it[paymentMethodType] = payment.paymentMethod.type.name
            it[cardLastFour] = payment.paymentMethod.cardLastFour
            it[cardBrand] = payment.paymentMethod.cardBrand?.name
            it[cardHolderName] = payment.paymentMethod.cardHolderName
            it[expirationMonth] = payment.paymentMethod.expirationMonth
            it[expirationYear] = payment.paymentMethod.expirationYear
        }
        payment.copy(id = id.value)
    }

    override fun findById(id: Long): PaymentRecord? = transaction(database) {
        PaymentsTable.selectAll()
            .where { PaymentsTable.id eq id }
            .map { it.toPaymentRecord() }
            .singleOrNull()
    }

    override fun findByOrderId(orderId: Long): PaymentRecord? = transaction(database) {
        PaymentsTable.selectAll()
            .where { PaymentsTable.orderId eq orderId }
            .map { it.toPaymentRecord() }
            .singleOrNull()
    }

    override fun findByBuyerId(buyerId: String, limit: Int, offset: Int): List<PaymentRecord> = transaction(database) {
        PaymentsTable.selectAll()
            .where { PaymentsTable.buyerId eq buyerId }
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toPaymentRecord() }
    }

    override fun updateStatus(
        paymentId: Long,
        status: PaymentStatus,
        transactionId: String?,
        failureReason: String?,
        processedAt: Instant?
    ): Boolean = transaction(database) {
        PaymentsTable.update({ PaymentsTable.id eq paymentId }) {
            it[PaymentsTable.status] = status.name
            if (transactionId != null) it[PaymentsTable.transactionId] = transactionId
            if (failureReason != null) it[PaymentsTable.failureReason] = failureReason
            if (processedAt != null) it[PaymentsTable.processedAt] = processedAt
        } > 0
    }

    private fun ResultRow.toPaymentRecord() = PaymentRecord(
        id = this[PaymentsTable.id].value,
        orderId = this[PaymentsTable.orderId],
        buyerId = this[PaymentsTable.buyerId],
        amount = this[PaymentsTable.amount],
        currency = this[PaymentsTable.currency],
        status = PaymentStatus.valueOf(this[PaymentsTable.status]),
        paymentMethod = PaymentMethodInfo(
            type = PaymentMethodType.valueOf(this[PaymentsTable.paymentMethodType]),
            cardLastFour = this[PaymentsTable.cardLastFour],
            cardBrand = this[PaymentsTable.cardBrand]?.let { CardBrand.valueOf(it) },
            cardHolderName = this[PaymentsTable.cardHolderName],
            expirationMonth = this[PaymentsTable.expirationMonth],
            expirationYear = this[PaymentsTable.expirationYear]
        ),
        transactionId = this[PaymentsTable.transactionId],
        failureReason = this[PaymentsTable.failureReason],
        createdAt = this[PaymentsTable.createdAt],
        processedAt = this[PaymentsTable.processedAt]
    )
}

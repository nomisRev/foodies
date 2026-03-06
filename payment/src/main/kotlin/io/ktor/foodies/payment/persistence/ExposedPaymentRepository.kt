package io.ktor.foodies.payment.persistence

import io.ktor.foodies.events.common.CardBrand
import io.ktor.foodies.events.common.PaymentMethodInfo
import io.ktor.foodies.events.common.PaymentMethodType
import io.ktor.foodies.payment.PaymentRecord
import io.ktor.foodies.payment.PaymentRepository
import io.ktor.foodies.payment.PaymentStatus
import org.jetbrains.exposed.v1.core.ResultRow
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ExposedPaymentRepository(private val database: Database) : PaymentRepository {

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

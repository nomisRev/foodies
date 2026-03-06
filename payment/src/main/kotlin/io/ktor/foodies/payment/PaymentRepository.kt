package io.ktor.foodies.payment

import kotlin.time.Instant

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

package io.ktor.foodies.payment

import io.ktor.foodies.payment.gateway.ChargeRequest
import io.ktor.foodies.payment.gateway.GatewayResult
import io.ktor.foodies.payment.gateway.PaymentGateway
import java.math.BigDecimal
import kotlin.time.Clock
import kotlin.time.Instant

interface PaymentService {
    /**
     * Process a payment for an order.
     * This is idempotent - processing the same orderId multiple times
     * returns the existing result without re-charging.
     */
    suspend fun processPayment(request: ProcessPaymentRequest): PaymentResult

    /**
     * Get payment record by order ID.
     */
    suspend fun getPaymentByOrderId(orderId: Long): PaymentRecord?

    /**
     * Get payment record by payment ID.
     */
    suspend fun getPaymentById(paymentId: Long): PaymentRecord?
}

data class ProcessPaymentRequest(
    val eventId: String,                           // For idempotency
    val orderId: Long,
    val buyerId: String,
    val amount: BigDecimal,
    val currency: String,
    val paymentMethod: PaymentMethodInfo
)

sealed interface PaymentResult {
    data class Success(
        val paymentId: Long,
        val transactionId: String,
        val processedAt: Instant
    ) : PaymentResult

    data class Failed(
        val reason: String,
        val code: PaymentFailureCode
    ) : PaymentResult

    data class AlreadyProcessed(
        val paymentRecord: PaymentRecord
    ) : PaymentResult
}

class PaymentServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val paymentGateway: PaymentGateway,
    private val clock: Clock = Clock.System
) : PaymentService {

    override suspend fun processPayment(request: ProcessPaymentRequest): PaymentResult {
        // Check for existing payment (idempotency)
        val existing = paymentRepository.findByOrderId(request.orderId)
        if (existing != null) {
            return PaymentResult.AlreadyProcessed(existing)
        }

        // Create pending payment record
        val pendingPayment = paymentRepository.create(
            PaymentRecord(
                id = 0,  // Auto-generated
                orderId = request.orderId,
                buyerId = request.buyerId,
                amount = request.amount,
                currency = request.currency,
                status = PaymentStatus.PENDING,
                paymentMethod = request.paymentMethod,
                transactionId = null,
                failureReason = null,
                createdAt = clock.now(),
                processedAt = null
            )
        )

        // Process with payment gateway
        return try {
            val gatewayResult = paymentGateway.charge(
                ChargeRequest(
                    amount = request.amount,
                    currency = request.currency,
                    paymentMethod = request.paymentMethod,
                    orderId = request.orderId,
                    buyerId = request.buyerId
                )
            )

            when (gatewayResult) {
                is GatewayResult.Success -> {
                    val processedAt = clock.now()
                    paymentRepository.updateStatus(
                        paymentId = pendingPayment.id,
                        status = PaymentStatus.SUCCEEDED,
                        transactionId = gatewayResult.transactionId,
                        processedAt = processedAt
                    )
                    PaymentResult.Success(
                        paymentId = pendingPayment.id,
                        transactionId = gatewayResult.transactionId,
                        processedAt = processedAt
                    )
                }
                is GatewayResult.Failed -> {
                    paymentRepository.updateStatus(
                        paymentId = pendingPayment.id,
                        status = PaymentStatus.FAILED,
                        failureReason = gatewayResult.reason
                    )
                    PaymentResult.Failed(
                        reason = gatewayResult.reason,
                        code = gatewayResult.code
                    )
                }
            }
        } catch (e: Exception) {
            paymentRepository.updateStatus(
                paymentId = pendingPayment.id,
                status = PaymentStatus.FAILED,
                failureReason = "Gateway error: ${e.message}"
            )
            PaymentResult.Failed(
                reason = "Payment gateway unavailable",
                code = PaymentFailureCode.GATEWAY_ERROR
            )
        }
    }

    override suspend fun getPaymentByOrderId(orderId: Long): PaymentRecord? {
        return paymentRepository.findByOrderId(orderId)
    }

    override suspend fun getPaymentById(paymentId: Long): PaymentRecord? {
        return paymentRepository.findById(paymentId)
    }
}

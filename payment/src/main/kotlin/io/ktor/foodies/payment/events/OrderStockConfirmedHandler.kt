package io.ktor.foodies.payment.events

import io.ktor.foodies.payment.PaymentFailureCode
import io.ktor.foodies.payment.PaymentResult
import io.ktor.foodies.payment.PaymentService
import io.ktor.foodies.payment.PaymentStatus
import io.ktor.foodies.payment.ProcessPaymentRequest
import java.util.UUID
import kotlin.time.Clock
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class OrderStockConfirmedEventHandler(
    private val paymentService: PaymentService,
    private val eventPublisher: EventPublisher,
    private val logger: Logger = LoggerFactory.getLogger(OrderStockConfirmedEventHandler::class.java)
) {

    suspend fun handle(event: OrderStockConfirmedEvent) {
        logger.info("Processing payment for order ${event.orderId}")

        val result = paymentService.processPayment(
            ProcessPaymentRequest(
                eventId = event.eventId,
                orderId = event.orderId,
                buyerId = event.buyerId,
                amount = event.totalAmount,
                currency = event.currency,
                paymentMethod = event.paymentMethod
            )
        )

        when (result) {
            is PaymentResult.Success -> {
                logger.info("Payment succeeded for order ${event.orderId}: ${result.transactionId}")
                eventPublisher.publish(
                    OrderPaymentSucceededEvent(
                        eventId = UUID.randomUUID().toString(),
                        orderId = event.orderId,
                        paymentId = result.paymentId,
                        transactionId = result.transactionId,
                        amount = event.totalAmount,
                        currency = event.currency,
                        processedAt = result.processedAt
                    )
                )
            }
            is PaymentResult.Failed -> {
                logger.warn("Payment failed for order ${event.orderId}: ${result.reason}")
                eventPublisher.publish(
                    OrderPaymentFailedEvent(
                        eventId = UUID.randomUUID().toString(),
                        orderId = event.orderId,
                        failureReason = result.reason,
                        failureCode = result.code,
                        occurredAt = Clock.System.now()
                    )
                )
            }
            is PaymentResult.AlreadyProcessed -> {
                logger.info("Payment already processed for order ${event.orderId}")
                // Re-publish the result for idempotent handling
                when (result.paymentRecord.status) {
                    PaymentStatus.SUCCEEDED -> eventPublisher.publish(
                        OrderPaymentSucceededEvent(
                            eventId = UUID.randomUUID().toString(),
                            orderId = event.orderId,
                            paymentId = result.paymentRecord.id,
                            transactionId = result.paymentRecord.transactionId!!,
                            amount = result.paymentRecord.amount,
                            currency = result.paymentRecord.currency,
                            processedAt = result.paymentRecord.processedAt!!
                        )
                    )
                    PaymentStatus.FAILED -> eventPublisher.publish(
                        OrderPaymentFailedEvent(
                            eventId = UUID.randomUUID().toString(),
                            orderId = event.orderId,
                            failureReason = result.paymentRecord.failureReason ?: "Unknown",
                            failureCode = PaymentFailureCode.UNKNOWN,
                            occurredAt = Clock.System.now()
                        )
                    )
                    else -> { /* PENDING/PROCESSING - wait for completion */ }
                }
            }
        }
    }
}

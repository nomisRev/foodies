package io.ktor.foodies.payment.events

import io.ktor.foodies.payment.PaymentFailureCode
import io.ktor.foodies.payment.PaymentResult
import io.ktor.foodies.payment.PaymentService
import io.ktor.foodies.payment.PaymentStatus
import io.ktor.foodies.payment.ProcessPaymentRequest
import io.ktor.foodies.rabbitmq.Message
import io.ktor.foodies.rabbitmq.consumeMessage
import java.util.UUID
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("OrderStockConfirmedEventHandler")

fun orderStockConfirmedEventConsumer(
    orderEvents: Flow<Message<OrderStockConfirmedEvent>>,
    paymentService: PaymentService,
    eventPublisher: EventPublisher
) = orderEvents.consumeMessage { event ->
    logger.info("Processing payment for order ${event.orderId}")
    event.handle(paymentService, eventPublisher)
}

suspend fun OrderStockConfirmedEvent.handle(paymentService: PaymentService, eventPublisher: EventPublisher) {
    val result = paymentService.processPayment(
        ProcessPaymentRequest(
            eventId = eventId,
            orderId = orderId,
            buyerId = buyerId,
            amount = totalAmount,
            currency = currency,
            paymentMethod = paymentMethod
        )
    )

    when (result) {
        is PaymentResult.Success -> {
            logger.info("Payment succeeded for order ${orderId}: ${result.transactionId}")
            eventPublisher.publish(
                OrderPaymentSucceededEvent(
                    eventId = UUID.randomUUID().toString(),
                    orderId = orderId,
                    paymentId = result.paymentId,
                    transactionId = result.transactionId,
                    amount = totalAmount,
                    currency = currency,
                    processedAt = result.processedAt
                )
            )
        }

        is PaymentResult.Failed -> {
            logger.warn("Payment failed for order ${orderId}: ${result.reason}")
            eventPublisher.publish(
                OrderPaymentFailedEvent(
                    eventId = UUID.randomUUID().toString(),
                    orderId = orderId,
                    failureReason = result.reason,
                    failureCode = result.code,
                    occurredAt = Clock.System.now()
                )
            )
        }

        is PaymentResult.AlreadyProcessed -> {
            logger.info("Payment already processed for order ${orderId}")
            // Re-publish the result for idempotent handling
            when (result.paymentRecord.status) {
                PaymentStatus.SUCCEEDED -> eventPublisher.publish(
                    OrderPaymentSucceededEvent(
                        eventId = UUID.randomUUID().toString(),
                        orderId = orderId,
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
                        orderId = orderId,
                        failureReason = result.paymentRecord.failureReason ?: "Unknown",
                        failureCode = PaymentFailureCode.UNKNOWN,
                        occurredAt = Clock.System.now()
                    )
                )

                else -> { /* PENDING/PROCESSING - wait for completion */
                }
            }
        }
    }
}

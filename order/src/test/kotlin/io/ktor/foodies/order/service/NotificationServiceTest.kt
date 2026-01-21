package io.ktor.foodies.order.service

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.events.common.CardBrand
import io.ktor.foodies.events.order.OrderStatus
import io.ktor.foodies.order.EmailConfig
import io.ktor.foodies.order.domain.Address
import io.ktor.foodies.order.domain.Order
import io.ktor.foodies.order.domain.OrderItem
import io.ktor.foodies.order.domain.PaymentMethod
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import kotlin.time.Instant

val notificationServiceSpec by testSuite {
    test("LoggingNotificationService should log without throwing") {
        val service = LoggingNotificationService()
        val order = createTestOrder()

        service.notifyStatusChange(order, OrderStatus.Submitted)
    }

    test("EmailNotificationService should handle missing SMTP server gracefully") {
        runTest {
            val emailConfig = EmailConfig(
                host = "nonexistent.smtp.server",
                port = 587,
                username = "test@example.com",
                password = "password",
                from = "noreply@foodies.local",
                useTls = true
            )
            val service = EmailNotificationService(emailConfig)
            val order = createTestOrder()

            service.notifyStatusChange(order, OrderStatus.Submitted)
        }
    }

    test("EmailNotificationService should handle email sending without blocking") {
        runTest {
            val emailConfig = EmailConfig(
                host = "localhost",
                port = 25,
                username = "test",
                password = "test",
                from = "test@foodies.local",
                useTls = false
            )
            val service = EmailNotificationService(emailConfig)
            val order = createTestOrder()

            service.notifyStatusChange(order, OrderStatus.Paid)
        }
    }
}

private fun createTestOrder() = Order(
    id = 1,
    requestId = "req-123",
    buyerId = "buyer-123",
    buyerName = "John Doe",
    buyerEmail = "john.doe@example.com",
    status = OrderStatus.Paid,
    deliveryAddress = Address(
        street = "123 Main St",
        city = "Springfield",
        state = "IL",
        country = "USA",
        zipCode = "62701"
    ),
    items = listOf(
        OrderItem(
            id = 1,
            menuItemId = 1,
            menuItemName = "Test Item",
            pictureUrl = "https://example.com/image.jpg",
            unitPrice = BigDecimal("29.99"),
            quantity = 1,
            discount = BigDecimal("0.00")
        )
    ),
    paymentMethod = PaymentMethod(
        id = 1,
        cardType = CardBrand.VISA,
        cardHolderName = "John Doe",
        cardNumber = "1234",
        expirationMonth = 12,
        expirationYear = 2030
    ),
    totalPrice = BigDecimal("29.99"),
    currency = "USD",
    description = "Test order",
    createdAt = Instant.DISTANT_PAST,
    updatedAt = Instant.DISTANT_PAST
)

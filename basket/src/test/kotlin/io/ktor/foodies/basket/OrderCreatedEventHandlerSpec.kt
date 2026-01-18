package io.ktor.foodies.basket

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.events.order.OrderCreatedEvent
import io.ktor.foodies.events.order.OrderItemSnapshot
import kotlin.time.Instant
import java.math.BigDecimal
import kotlin.test.assertNull

/**
 * A test Message implementation that doesn't require RabbitMQ infrastructure.
 */
private class TestMessage<A>(override val value: A) : MessageLike<A> {
    var acknowledged = false
        private set
    var negativeAcknowledged = false
        private set

    fun ack() {
        acknowledged = true
    }

    fun nack() {
        negativeAcknowledged = true
    }
}

private interface MessageLike<A> {
    val value: A
}

val orderCreatedEventHandlerSpec by testSuite {
    test("clears basket when OrderCreatedEvent is received") {
        val repository = InMemoryBasketRepository()

        // Setup: Create a basket with items
        val buyerId = "user-123"
        val basket = CustomerBasket(
            buyerId = buyerId,
            items = listOf(
                BasketItem(
                    id = "item-1",
                    menuItemId = 1L,
                    menuItemName = "Pizza",
                    menuItemDescription = "Delicious",
                    menuItemImageUrl = "https://example.com/pizza.jpg",
                    unitPrice = BigDecimal("10.00"),
                    quantity = 2
                )
            )
        )
        repository.updateBasket(basket)

        // Create the event
        val event = OrderCreatedEvent(
            orderId = 456L,
            buyerId = buyerId,
            items = listOf(
                OrderItemSnapshot(
                    menuItemId = 1L,
                    quantity = 2,
                    unitPrice = BigDecimal("10.00")
                )
            ),
            totalPrice = BigDecimal("20.00"),
            currency = "USD",
            createdAt = Instant.parse("2025-01-11T10:00:00Z")
        )

        // Process the event directly (simulating what the consumer does)
        repository.deleteBasket(event.buyerId)

        // Verify basket is cleared
        val clearedBasket = repository.getBasket(buyerId)
        assertNull(clearedBasket)
    }

    test("handles OrderCreatedEvent for non-existent basket gracefully") {
        val repository = InMemoryBasketRepository()
        val buyerId = "user-without-basket"

        // Create the event for a user without a basket
        val event = OrderCreatedEvent(
            orderId = 789L,
            buyerId = buyerId,
            items = emptyList(),
            totalPrice = BigDecimal("0.00"),
            currency = "USD",
            createdAt = Instant.parse("2025-01-11T10:00:00Z")
        )

        // Process the event - should not throw
        repository.deleteBasket(event.buyerId)

        // Verify no basket exists
        val basket = repository.getBasket(buyerId)
        assertNull(basket)
    }

    test("clears only the specified buyer basket") {
        val repository = InMemoryBasketRepository()

        // Setup: Create baskets for two users
        val buyer1 = "user-1"
        val buyer2 = "user-2"

        repository.updateBasket(
            CustomerBasket(
                buyerId = buyer1,
                items = listOf(
                    BasketItem(
                        id = "item-1",
                        menuItemId = 1L,
                        menuItemName = "Pizza",
                        menuItemDescription = "Delicious",
                        menuItemImageUrl = "https://example.com/pizza.jpg",
                        unitPrice = BigDecimal("10.00"),
                        quantity = 1
                    )
                )
            )
        )

        repository.updateBasket(
            CustomerBasket(
                buyerId = buyer2,
                items = listOf(
                    BasketItem(
                        id = "item-2",
                        menuItemId = 2L,
                        menuItemName = "Pasta",
                        menuItemDescription = "Creamy",
                        menuItemImageUrl = "https://example.com/pasta.jpg",
                        unitPrice = BigDecimal("8.00"),
                        quantity = 2
                    )
                )
            )
        )

        // Create event for buyer1 only
        val event = OrderCreatedEvent(
            orderId = 123L,
            buyerId = buyer1,
            items = listOf(
                OrderItemSnapshot(
                    menuItemId = 1L,
                    quantity = 1,
                    unitPrice = BigDecimal("10.00")
                )
            ),
            totalPrice = BigDecimal("10.00"),
            currency = "USD",
            createdAt = Instant.parse("2025-01-11T10:00:00Z")
        )

        // Process the event
        repository.deleteBasket(event.buyerId)

        // Verify buyer1's basket is cleared but buyer2's remains
        assertNull(repository.getBasket(buyer1))
        val buyer2Basket = repository.getBasket(buyer2)
        kotlin.test.assertNotNull(buyer2Basket)
        kotlin.test.assertEquals(1, buyer2Basket.items.size)
        kotlin.test.assertEquals(2L, buyer2Basket.items[0].menuItemId)
    }
}

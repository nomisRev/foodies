package io.ktor.foodies.order

import io.ktor.foodies.order.client.CustomerBasket
import io.ktor.foodies.order.client.BasketClient
import io.ktor.foodies.order.client.BasketItem
import io.ktor.foodies.order.domain.*
import io.ktor.foodies.order.repository.IdempotencyRepository
import io.ktor.foodies.order.repository.OrderRepository
import io.ktor.foodies.order.repository.ProcessedRequest
import io.ktor.foodies.order.service.DefaultOrderService
import io.ktor.foodies.order.service.IdempotencyService
import io.ktor.foodies.order.service.OrderEventPublisher
import io.ktor.foodies.server.SerializableBigDecimal
import java.math.BigDecimal
import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class GracefulCancellationTest {

    private val fakeIdempotencyRepository = object : IdempotencyRepository {
        val requests = mutableMapOf<UUID, ProcessedRequest>()
        override fun findByRequestId(requestId: UUID): ProcessedRequest? = requests[requestId]
        override fun save(request: ProcessedRequest) {
            requests[request.requestId] = request
        }
    }

    private val idempotencyService = IdempotencyService(fakeIdempotencyRepository)

    private val fakeOrderRepository = object : OrderRepository {
        val orders = mutableListOf<Order>()
        override fun findById(id: Long): Order? = orders.find { it.id == id }
        override fun findByRequestId(requestId: String): Order? = orders.find { it.requestId == requestId }
        override fun findByBuyerId(buyerId: String, offset: Long, limit: Int, status: OrderStatus?): PaginatedOrders = TODO()
        override fun findAll(offset: Long, limit: Int, status: OrderStatus?, buyerId: String?): PaginatedOrders = TODO()
        override fun create(order: CreateOrder): Order {
            val newOrder = Order(
                id = (orders.size + 1).toLong(),
                requestId = order.requestId,
                buyerId = order.buyerId,
                buyerEmail = order.buyerEmail,
                buyerName = order.buyerName,
                status = OrderStatus.Submitted,
                deliveryAddress = order.deliveryAddress,
                items = order.items.mapIndexed { index, item ->
                    OrderItem(
                        id = (index + 1).toLong(),
                        menuItemId = item.menuItemId,
                        menuItemName = item.menuItemName,
                        pictureUrl = item.pictureUrl,
                        unitPrice = item.unitPrice,
                        quantity = item.quantity,
                        discount = BigDecimal.ZERO
                    )
                },
                paymentMethod = null,
                totalPrice = order.totalPrice,
                description = "Order submitted",
                history = emptyList(),
                createdAt = Instant.fromEpochMilliseconds(0),
                updatedAt = Instant.fromEpochMilliseconds(0)
            )
            orders.add(newOrder)
            return newOrder
        }
        override fun update(order: Order): Order {
            orders.removeIf { it.id == order.id }
            orders.add(order)
            return order
        }
    }

    private val fakeBasketClient = object : BasketClient {
        override suspend fun getBasket(buyerId: String, token: String): CustomerBasket? = null
    }

    private val fakeEventPublisher = object : OrderEventPublisher {
        val events = mutableListOf<Any>()
        override suspend fun publish(event: OrderCreatedEvent) { events.add(event) }
        override suspend fun publish(event: OrderCancelledEvent) { events.add(event) }
        override suspend fun publish(event: OrderStatusChangedEvent) { events.add(event) }
        override suspend fun publish(event: OrderAwaitingValidationEvent) { events.add(event) }
        override suspend fun publish(event: StockReturnedEvent) { events.add(event) }
    }

    private val orderService = DefaultOrderService(fakeOrderRepository, fakeBasketClient, fakeEventPublisher, idempotencyService)

    @Test
    fun `cancelling order in StockConfirmed state should publish stock return information`() = runTest {
        // Setup: Create an order and move it to StockConfirmed
        val order = fakeOrderRepository.create(CreateOrder(
            requestId = "req-1",
            buyerId = "buyer-1",
            buyerEmail = "buyer@test.com",
            buyerName = "John",
            deliveryAddress = Address("Street", "City", "State", "Country", "12345"),
            items = listOf(
                CreateOrderItem(1L, "Burger", "url", BigDecimal("10.00"), 2)
            ),
            totalPrice = BigDecimal("20.00"),
            paymentDetails = PaymentDetails(CardType.Visa, "1234567812345678", "John", "123", 12, 2030)
        ))
        
        val stockConfirmedOrder = order.copy(status = OrderStatus.StockConfirmed)
        fakeOrderRepository.update(stockConfirmedOrder)
        
        // Action: Cancel the order
        orderService.cancelOrder(UUID.randomUUID(), order.id, "buyer-1", "User cancelled")
        
        // Verification: Check for stock return information
        val stockReturnedEvents = fakeEventPublisher.events.filterIsInstance<StockReturnedEvent>()
        assertTrue(stockReturnedEvents.isNotEmpty(), "Expected StockReturnedEvent to be published")
        assertEquals(order.id, stockReturnedEvents[0].orderId)
        assertEquals(1, stockReturnedEvents[0].items.size)
        assertEquals(1L, stockReturnedEvents[0].items[0].menuItemId)
        assertEquals(2, stockReturnedEvents[0].items[0].quantity)
    }
}

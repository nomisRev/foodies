package io.ktor.foodies.order.events.handlers

import io.ktor.foodies.order.domain.*
import io.ktor.foodies.order.repository.OrderRepository
import io.ktor.foodies.order.service.DefaultOrderService
import io.ktor.foodies.order.service.OrderEventPublisher
import io.ktor.foodies.order.client.BasketClient
import io.ktor.foodies.order.client.CustomerBasket
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class StockEventHandlerTest {

    private val fakeOrderRepository = object : OrderRepository {
        val orders = mutableListOf<Order>()
        override fun findById(id: Long): Order? = orders.find { it.id == id }
        override fun findByRequestId(requestId: String): Order? = null
        override fun findByBuyerId(buyerId: String, offset: Long, limit: Int, status: OrderStatus?): PaginatedOrders = TODO()
        override fun findAll(offset: Long, limit: Int, status: OrderStatus?, buyerId: String?): PaginatedOrders = TODO()
        override fun create(order: CreateOrder): Order = TODO()
        override fun update(order: Order): Order {
            orders.removeIf { it.id == order.id }
            orders.add(order)
            return order
        }
    }

    private val fakeEventPublisher = object : OrderEventPublisher {
        val events = mutableListOf<Any>()
        override suspend fun publish(event: OrderCreatedEvent) { events.add(event) }
        override suspend fun publish(event: OrderCancelledEvent) { events.add(event) }
        override suspend fun publish(event: OrderStatusChangedEvent) { events.add(event) }
        override suspend fun publish(event: OrderAwaitingValidationEvent) { events.add(event) }
    }

    private val fakeBasketClient = object : BasketClient {
        override suspend fun getBasket(buyerId: String, token: String): CustomerBasket? = null
    }

    private val orderService = DefaultOrderService(fakeOrderRepository, fakeBasketClient, fakeEventPublisher)
    private val confirmedHandler = StockConfirmedEventHandler(orderService)
    private val rejectedHandler = StockRejectedEventHandler(orderService)

    @Test
    fun `StockConfirmedEventHandler should update order status to StockConfirmed`() = runTest {
        val order = createOrder(OrderStatus.AwaitingValidation)
        fakeOrderRepository.orders.add(order)

        confirmedHandler.handle(StockConfirmedEvent(order.id, Instant.fromEpochMilliseconds(0)))

        val updatedOrder = fakeOrderRepository.findById(order.id)
        assertEquals(OrderStatus.StockConfirmed, updatedOrder?.status)
        
        val statusEvent = fakeEventPublisher.events.filterIsInstance<OrderStatusChangedEvent>().last()
        assertEquals(OrderStatus.StockConfirmed, statusEvent.newStatus)
    }

    @Test
    fun `StockRejectedEventHandler should cancel order`() = runTest {
        val order = createOrder(OrderStatus.AwaitingValidation)
        fakeOrderRepository.orders.add(order)

        rejectedHandler.handle(StockRejectedEvent(
            orderId = order.id,
            rejectedItems = listOf(RejectedItem(1, "Burger", 2, 1)),
            rejectedAt = Instant.fromEpochMilliseconds(0)
        ))

        val updatedOrder = fakeOrderRepository.findById(order.id)
        assertEquals(OrderStatus.Cancelled, updatedOrder?.status)
        assert(updatedOrder?.description?.contains("Stock rejected") == true)
        
        val cancelEvent = fakeEventPublisher.events.filterIsInstance<OrderCancelledEvent>().last()
        assertEquals(order.id, cancelEvent.orderId)
    }

    private fun createOrder(status: OrderStatus) = Order(
        id = 1,
        requestId = "req-1",
        buyerId = "user-1",
        buyerEmail = "user@test.com",
        buyerName = "User",
        status = status,
        deliveryAddress = Address("S", "C", "S", "C", "Z"),
        items = emptyList(),
        paymentMethod = null,
        totalPrice = BigDecimal.TEN,
        description = null,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0)
    )
}

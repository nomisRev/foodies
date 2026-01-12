package io.ktor.foodies.order

import io.ktor.foodies.order.client.CustomerBasket
import io.ktor.foodies.order.client.BasketClient
import io.ktor.foodies.order.client.BasketItem
import io.ktor.foodies.order.domain.*
import io.ktor.foodies.order.repository.OrderRepository
import io.ktor.foodies.order.service.DefaultOrderService
import io.ktor.foodies.order.service.OrderEventPublisher
import java.math.BigDecimal
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant

class GetOrderDetailsTest {

    private val fakeOrderRepository = object : OrderRepository {
        val orders = mutableListOf<Order>()
        override fun findById(id: Long): Order? = orders.find { it.id == id }
        override fun findByRequestId(requestId: String): Order? = orders.find { it.requestId == requestId }
        override fun findByBuyerId(
            buyerId: String,
            offset: Long,
            limit: Int,
            status: OrderStatus?
        ): PaginatedOrders {
            return PaginatedOrders(emptyList(), 0, offset, limit)
        }
        override fun findAll(
            offset: Long,
            limit: Int,
            status: OrderStatus?,
            buyerId: String?
        ): PaginatedOrders {
            return PaginatedOrders(emptyList(), 0, offset, limit)
        }
        override fun create(order: CreateOrder): Order {
            val newOrder = Order(
                id = (orders.size + 1).toLong(),
                requestId = order.requestId,
                buyerId = order.buyerId,
                buyerEmail = order.buyerEmail,
                buyerName = order.buyerName,
                status = OrderStatus.Submitted,
                deliveryAddress = order.deliveryAddress,
                items = emptyList(),
                paymentMethod = null,
                totalPrice = order.totalPrice,
                description = "Order submitted",
                createdAt = Instant.fromEpochMilliseconds(0),
                updatedAt = Instant.fromEpochMilliseconds(0)
            )
            orders.add(newOrder)
            return newOrder
        }
        override fun update(order: Order): Order {
            return order
        }
    }

    private val fakeBasketClient = object : BasketClient {
        override suspend fun getBasket(buyerId: String, token: String): CustomerBasket? = null
    }

    private val fakeEventPublisher = object : OrderEventPublisher {
        override suspend fun publish(event: OrderCreatedEvent) {}
        override suspend fun publish(event: OrderCancelledEvent) {}
        override suspend fun publish(event: OrderStatusChangedEvent) {}
        override suspend fun publish(event: OrderAwaitingValidationEvent) {}
    }

    private val orderService = DefaultOrderService(fakeOrderRepository, fakeBasketClient, fakeEventPublisher)

    @Test
    fun `should get order details`() = kotlinx.coroutines.test.runTest {
        val existingOrder = Order(
            id = 123,
            requestId = "req-123",
            buyerId = "user-1",
            buyerEmail = "user1@test.com",
            buyerName = "User One",
            status = OrderStatus.Submitted,
            deliveryAddress = Address("Street", "City", "State", "Country", "12345"),
            items = emptyList(),
            paymentMethod = null,
            totalPrice = BigDecimal("10.00"),
            description = null,
            createdAt = Instant.fromEpochMilliseconds(0),
            updatedAt = Instant.fromEpochMilliseconds(0)
        )
        fakeOrderRepository.orders.add(existingOrder)

        // This will fail to compile initially because getOrder is not in OrderService
        val order = orderService.getOrder(123, "user-1")

        assertNotNull(order)
        assertEquals(123, order.id)
        assertEquals("user-1", order.buyerId)
    }

    @Test
    fun `should throw OrderNotFoundException when order does not exist`() = kotlinx.coroutines.test.runTest {
        assertThrows<OrderNotFoundException> {
            orderService.getOrder(999, "user-1")
        }
    }

    @Test
    fun `should throw OrderForbiddenException when order belongs to different user`() = kotlinx.coroutines.test.runTest {
        val existingOrder = Order(
            id = 456,
            requestId = "req-456",
            buyerId = "user-2",
            buyerEmail = "user2@test.com",
            buyerName = "User Two",
            status = OrderStatus.Submitted,
            deliveryAddress = Address("Street", "City", "State", "Country", "12345"),
            items = emptyList(),
            paymentMethod = null,
            totalPrice = BigDecimal("10.00"),
            description = null,
            createdAt = Instant.fromEpochMilliseconds(0),
            updatedAt = Instant.fromEpochMilliseconds(0)
        )
        fakeOrderRepository.orders.add(existingOrder)

        assertThrows<OrderForbiddenException> {
            orderService.getOrder(456, "user-1")
        }
    }
}

package io.ktor.foodies.order

import io.ktor.foodies.order.client.CustomerBasket
import io.ktor.foodies.order.client.BasketClient
import io.ktor.foodies.order.client.BasketItem
import io.ktor.foodies.order.domain.*
import io.ktor.foodies.order.repository.OrderRepository
import io.ktor.foodies.order.service.DefaultOrderService
import io.ktor.foodies.order.service.OrderEventPublisher
import io.ktor.foodies.server.SerializableBigDecimal
import java.math.BigDecimal
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class OrderServiceTest {

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
            val filtered = orders.filter { it.buyerId == buyerId && (status == null || it.status == status) }
            val sorted = filtered.sortedByDescending { it.createdAt }
            val paged = sorted.drop(offset.toInt()).take(limit)
            val summaries = paged.map {
                OrderSummary(
                    id = it.id,
                    status = it.status,
                    totalPrice = it.totalPrice,
                    itemCount = it.items.sumOf { item -> item.quantity },
                    createdAt = it.createdAt
                )
            }
            return PaginatedOrders(summaries, filtered.size.toLong(), offset, limit)
        }
        override fun findAll(
            offset: Long,
            limit: Int,
            status: OrderStatus?,
            buyerId: String?
        ): PaginatedOrders {
            val filtered = orders.filter { (buyerId == null || it.buyerId == buyerId) && (status == null || it.status == status) }
            val sorted = filtered.sortedByDescending { it.createdAt }
            val paged = sorted.drop(offset.toInt()).take(limit)
            val summaries = paged.map {
                OrderSummary(
                    id = it.id,
                    status = it.status,
                    totalPrice = it.totalPrice,
                    itemCount = it.items.sumOf { item -> item.quantity },
                    createdAt = it.createdAt
                )
            }
            return PaginatedOrders(summaries, filtered.size.toLong(), offset, limit)
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
                paymentMethod = PaymentMethod(1, order.paymentDetails.cardType, order.paymentDetails.cardHolderName, order.paymentDetails.cardNumber.takeLast(4), order.paymentDetails.expirationMonth, order.paymentDetails.expirationYear),
                totalPrice = order.totalPrice,
                description = "Order submitted",
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
        override suspend fun getBasket(buyerId: String, token: String): CustomerBasket? {
            return CustomerBasket(
                buyerId = buyerId,
                items = listOf(
                    BasketItem(1, "Burger", "url", BigDecimal("10.00"), 2)
                )
            )
        }
    }

    private val fakeEventPublisher = object : OrderEventPublisher {
        val createdEvents = mutableListOf<OrderCreatedEvent>()
        val cancelledEvents = mutableListOf<OrderCancelledEvent>()
        val statusChangedEvents = mutableListOf<OrderStatusChangedEvent>()
        override suspend fun publish(event: OrderCreatedEvent) {
            createdEvents.add(event)
        }
        override suspend fun publish(event: OrderCancelledEvent) {
            cancelledEvents.add(event)
        }
        override suspend fun publish(event: OrderStatusChangedEvent) {
            statusChangedEvents.add(event)
        }
    }

    private val orderService = DefaultOrderService(fakeOrderRepository, fakeBasketClient, fakeEventPublisher)

    @Test
    fun `should create order and publish event`() = kotlinx.coroutines.test.runTest {
        val request = CreateOrderRequest(
            street = "Street",
            city = "City",
            state = "State",
            country = "Country",
            zipCode = "12345",
            paymentDetails = PaymentDetails(
                cardType = CardType.Visa,
                cardNumber = "1234567812345678",
                cardHolderName = "John Doe",
                cardSecurityNumber = "123",
                expirationMonth = 12,
                expirationYear = 2025
            )
        )

        val order = orderService.createOrder("req-1", "buyer-1", "buyer@test.com", "John", request, "token")

        assertEquals(1, order.id)
        assertEquals("buyer-1", order.buyerId)
        assertEquals(BigDecimal("20.00"), order.totalPrice)
        assertEquals(1, fakeEventPublisher.createdEvents.size)
        assertEquals(order.id, fakeEventPublisher.createdEvents[0].orderId)
    }

    @Test
    fun `should return existing order for same request id`() = kotlinx.coroutines.test.runTest {
        val request = CreateOrderRequest(
            street = "Street",
            city = "City",
            state = "State",
            country = "Country",
            zipCode = "12345",
            paymentDetails = PaymentDetails(
                cardType = CardType.Visa,
                cardNumber = "1234567812345678",
                cardHolderName = "John Doe",
                cardSecurityNumber = "123",
                expirationMonth = 12,
                expirationYear = 2025
            )
        )

        val order1 = orderService.createOrder("req-1", "buyer-1", "buyer@test.com", "John", request, "token")
        val order2 = orderService.createOrder("req-1", "buyer-1", "buyer@test.com", "John", request, "token")

        assertEquals(order1.id, order2.id)
        assertEquals(1, fakeOrderRepository.orders.size)
        assertEquals(1, fakeEventPublisher.createdEvents.size)
    }

    @Test
    fun `should return paginated orders`() = kotlinx.coroutines.test.runTest {
        val request = CreateOrderRequest(
            street = "Street",
            city = "City",
            state = "State",
            country = "Country",
            zipCode = "12345",
            paymentDetails = PaymentDetails(
                cardType = CardType.Visa,
                cardNumber = "1234567812345678",
                cardHolderName = "John Doe",
                cardSecurityNumber = "123",
                expirationMonth = 12,
                expirationYear = 2025
            )
        )

        orderService.createOrder("req-1", "buyer-1", "buyer@test.com", "John", request, "token")
        orderService.createOrder("req-2", "buyer-1", "buyer@test.com", "John", request, "token")

        val paginated = orderService.getOrders("buyer-1", 0, 10)

        assertEquals(2, paginated.orders.size)
        assertEquals(2, paginated.total)
    }

    @Test
    fun `should cancel order and publish event`() = kotlinx.coroutines.test.runTest {
        val request = CreateOrderRequest(
            street = "Street",
            city = "City",
            state = "State",
            country = "Country",
            zipCode = "12345",
            paymentDetails = PaymentDetails(
                cardType = CardType.Visa,
                cardNumber = "1234567812345678",
                cardHolderName = "John Doe",
                cardSecurityNumber = "123",
                expirationMonth = 12,
                expirationYear = 2025
            )
        )

        val order = orderService.createOrder("req-1", "buyer-1", "buyer@test.com", "John", request, "token")
        
        val cancelledOrder = orderService.cancelOrder(order.id, "buyer-1", "Changed my mind")
        
        assertEquals(OrderStatus.Cancelled, cancelledOrder.status)
        assertEquals("Changed my mind", cancelledOrder.description)
        assertEquals(1, fakeEventPublisher.cancelledEvents.size)
        assertEquals(order.id, fakeEventPublisher.cancelledEvents[0].orderId)
        assertEquals("Changed my mind", fakeEventPublisher.cancelledEvents[0].reason)
        
        assertEquals(1, fakeEventPublisher.statusChangedEvents.size)
        assertEquals(OrderStatus.Submitted, fakeEventPublisher.statusChangedEvents[0].oldStatus)
        assertEquals(OrderStatus.Cancelled, fakeEventPublisher.statusChangedEvents[0].newStatus)
    }

    @Test
    fun `should ship order and publish event`() = kotlinx.coroutines.test.runTest {
        val request = CreateOrderRequest(
            street = "Street",
            city = "City",
            state = "State",
            country = "Country",
            zipCode = "12345",
            paymentDetails = PaymentDetails(
                cardType = CardType.Visa,
                cardNumber = "1234567812345678",
                cardHolderName = "John Doe",
                cardSecurityNumber = "123",
                expirationMonth = 12,
                expirationYear = 2025
            )
        )

        val order = orderService.createOrder("req-1", "buyer-1", "buyer@test.com", "John", request, "token")
        
        // Manually set status to Paid
        val paidOrder = order.copy(status = OrderStatus.Paid)
        fakeOrderRepository.update(paidOrder)
        
        val shippedOrder = orderService.shipOrder(order.id)
        
        assertEquals(OrderStatus.Shipped, shippedOrder.status)
        assertEquals(1, fakeEventPublisher.statusChangedEvents.size)
        assertEquals(OrderStatus.Paid, fakeEventPublisher.statusChangedEvents[0].oldStatus)
        assertEquals(OrderStatus.Shipped, fakeEventPublisher.statusChangedEvents[0].newStatus)
    }

    @Test
    fun `should throw error if shipping order that is not paid`() = kotlinx.coroutines.test.runTest {
        val request = CreateOrderRequest(
            street = "Street",
            city = "City",
            state = "State",
            country = "Country",
            zipCode = "12345",
            paymentDetails = PaymentDetails(
                cardType = CardType.Visa,
                cardNumber = "1234567812345678",
                cardHolderName = "John Doe",
                cardSecurityNumber = "123",
                expirationMonth = 12,
                expirationYear = 2025
            )
        )

        val order = orderService.createOrder("req-1", "buyer-1", "buyer@test.com", "John", request, "token")
        
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            orderService.shipOrder(order.id)
        }
    }
}

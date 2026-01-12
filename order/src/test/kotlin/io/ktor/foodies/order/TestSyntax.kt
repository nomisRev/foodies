package io.ktor.foodies.order

import de.infix.testBalloon.framework.core.TestSuite
import io.ktor.foodies.order.client.BasketClient
import io.ktor.foodies.order.client.CustomerBasket
import io.ktor.foodies.order.domain.*
import io.ktor.foodies.order.repository.IdempotencyRepository
import io.ktor.foodies.order.repository.OrderRepository
import io.ktor.foodies.order.repository.ProcessedRequest
import io.ktor.foodies.order.service.DefaultOrderService
import io.ktor.foodies.order.service.IdempotencyService
import io.ktor.foodies.order.service.OrderEventPublisher
import io.ktor.foodies.server.test.ctxSuite
import java.util.UUID
import kotlin.time.Instant

/**
 * Holds test dependencies for the OrderService.
 */
data class TestContext(
    val idempotencyRepository: InMemoryIdempotencyRepository,
    val orderRepository: InMemoryOrderRepository,
    val basketClient: InMemoryBasketClient,
    val eventPublisher: InMemoryOrderEventPublisher,
    val idempotencyService: IdempotencyService,
    val service: DefaultOrderService
)

fun createTestContext(): TestContext {
    val idempotencyRepository = InMemoryIdempotencyRepository()
    val idempotencyService = IdempotencyService(idempotencyRepository)
    val orderRepository = InMemoryOrderRepository()
    val basketClient = InMemoryBasketClient()
    val eventPublisher = InMemoryOrderEventPublisher()
    val service = DefaultOrderService(orderRepository, basketClient, eventPublisher, idempotencyService)
    return TestContext(
        idempotencyRepository = idempotencyRepository,
        orderRepository = orderRepository,
        basketClient = basketClient,
        eventPublisher = eventPublisher,
        idempotencyService = idempotencyService,
        service = service
    )
}

class InMemoryIdempotencyRepository : IdempotencyRepository {
    val requests = mutableMapOf<UUID, ProcessedRequest>()
    override fun findByRequestId(requestId: UUID): ProcessedRequest? = requests[requestId]
    override fun save(request: ProcessedRequest) {
        requests[request.requestId] = request
    }
}

class InMemoryOrderRepository : OrderRepository {
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
                description = it.description,
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
                description = it.description,
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
                    discount = 0.toBigDecimal()
                )
            },
            paymentMethod = PaymentMethod(
                1,
                order.paymentDetails.cardType,
                order.paymentDetails.cardHolderName,
                order.paymentDetails.cardNumber.takeLast(4),
                order.paymentDetails.expirationMonth,
                order.paymentDetails.expirationYear
            ),
            totalPrice = order.totalPrice,
            currency = order.currency,
            description = "Order submitted",
            history = listOf(
                OrderHistoryEntry(
                    id = 1L,
                    orderId = (orders.size + 1).toLong(),
                    status = OrderStatus.Submitted,
                    description = "Order submitted",
                    createdAt = Instant.fromEpochMilliseconds(0)
                )
            ),
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

class InMemoryBasketClient : BasketClient {
    var basket: CustomerBasket? = null

    override suspend fun getBasket(buyerId: String, token: String): CustomerBasket? = basket
}

class InMemoryOrderEventPublisher : OrderEventPublisher {
    val createdEvents = mutableListOf<OrderCreatedEvent>()
    val cancelledEvents = mutableListOf<OrderCancelledEvent>()
    val statusChangedEvents = mutableListOf<OrderStatusChangedEvent>()
    val stockConfirmedEvents = mutableListOf<OrderStockConfirmedEvent>()
    val awaitingValidationEvents = mutableListOf<OrderAwaitingValidationEvent>()
    val stockReturnedEvents = mutableListOf<StockReturnedEvent>()

    override suspend fun publish(event: OrderCreatedEvent) {
        createdEvents.add(event)
    }

    override suspend fun publish(event: OrderCancelledEvent) {
        cancelledEvents.add(event)
    }

    override suspend fun publish(event: OrderStatusChangedEvent) {
        statusChangedEvents.add(event)
    }

    override suspend fun publish(event: OrderStockConfirmedEvent) {
        stockConfirmedEvents.add(event)
    }

    override suspend fun publish(event: OrderAwaitingValidationEvent) {
        awaitingValidationEvents.add(event)
    }

    override suspend fun publish(event: StockReturnedEvent) {
        stockReturnedEvents.add(event)
    }
}

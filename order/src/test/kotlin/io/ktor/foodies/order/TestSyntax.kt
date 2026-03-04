package io.ktor.foodies.order

import io.ktor.foodies.events.order.*
import io.ktor.foodies.order.fulfillment.DefaultFulfillmentService
import io.ktor.foodies.order.fulfillment.FulfillmentEventPublisher
import io.ktor.foodies.order.persistence.OrderRepository

import io.ktor.foodies.order.placement.BasketClient
import io.ktor.foodies.order.placement.CreateOrder
import io.ktor.foodies.order.placement.CustomerBasket
import io.ktor.foodies.order.placement.DefaultPlacementService
import io.ktor.foodies.order.placement.GracePeriodExpiredEvent
import io.ktor.foodies.order.placement.PlacementEventPublisher
import io.ktor.foodies.order.placement.PlacementRepository
import io.ktor.foodies.order.tracking.DefaultTrackingService
import io.ktor.foodies.order.tracking.GetOrderResult
import io.ktor.foodies.order.tracking.OrderSummary
import io.ktor.foodies.order.tracking.PaginatedOrders
import io.ktor.foodies.order.tracking.TrackingEventPublisher
import io.ktor.foodies.order.tracking.TrackingRepository
import kotlin.time.Duration
import kotlin.time.Instant

data class TestContext(
    val orderRepository: InMemoryOrderRepository,
    val basketClient: InMemoryBasketClient,
    val placementEventPublisher: InMemoryPlacementEventPublisher,
    val trackingEventPublisher: InMemoryTrackingEventPublisher,
    val fulfillmentEventPublisher: InMemoryFulfillmentEventPublisher,
    val placementService: DefaultPlacementService,
    val trackingService: DefaultTrackingService,
    val fulfillmentService: DefaultFulfillmentService,
)

fun createTestContext(): TestContext {
    val orderRepository = InMemoryOrderRepository()
    val basketClient = InMemoryBasketClient()
    val placementEventPublisher = InMemoryPlacementEventPublisher()
    val trackingEventPublisher = InMemoryTrackingEventPublisher()
    val fulfillmentEventPublisher = InMemoryFulfillmentEventPublisher()
    val placementService = DefaultPlacementService(orderRepository, basketClient, placementEventPublisher, OrderConfig(30))
    val trackingService = DefaultTrackingService(orderRepository, orderRepository, trackingEventPublisher)
    val fulfillmentService = DefaultFulfillmentService(orderRepository, fulfillmentEventPublisher)
    return TestContext(
        orderRepository = orderRepository,
        basketClient = basketClient,
        placementEventPublisher = placementEventPublisher,
        trackingEventPublisher = trackingEventPublisher,
        fulfillmentEventPublisher = fulfillmentEventPublisher,
        placementService = placementService,
        trackingService = trackingService,
        fulfillmentService = fulfillmentService,
    )
}

class InMemoryOrderRepository : OrderRepository, PlacementRepository, TrackingRepository {
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
        val filtered =
            orders.filter { (buyerId == null || it.buyerId == buyerId) && (status == null || it.status == status) }
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

    override fun create(createOrder: CreateOrder): Order {
        val newOrder = Order(
            id = (orders.size + 1).toLong(),
            requestId = createOrder.requestId,
            buyerId = createOrder.buyerId,
            buyerEmail = createOrder.buyerEmail,
            buyerName = createOrder.buyerName,
            status = OrderStatus.Submitted,
            deliveryAddress = createOrder.deliveryAddress,
            items = createOrder.items.mapIndexed { index, item ->
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
                createOrder.paymentDetails.cardType,
                createOrder.paymentDetails.cardHolderName,
                createOrder.paymentDetails.cardNumber.takeLast(4),
                createOrder.paymentDetails.expirationMonth,
                createOrder.paymentDetails.expirationYear
            ),
            totalPrice = createOrder.totalPrice,
            currency = createOrder.currency,
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

class InMemoryPlacementEventPublisher : PlacementEventPublisher {
    val createdEvents = mutableListOf<OrderCreatedEvent>()
    val delayedEvents = mutableListOf<Pair<GracePeriodExpiredEvent, Int>>()

    override suspend fun publish(event: OrderCreatedEvent) {
        createdEvents.add(event)
    }

    override suspend fun publish(event: GracePeriodExpiredEvent, delay: Duration) {
        delayedEvents.add(event to delay.inWholeMilliseconds.toInt())
    }
}

class InMemoryTrackingEventPublisher : TrackingEventPublisher {
    val cancelledEvents = mutableListOf<OrderCancelledEvent>()
    val stockReturnedEvents = mutableListOf<StockReturnedEvent>()
    val statusChangedEvents = mutableListOf<OrderStatusChangedEvent>()

    override suspend fun publish(event: OrderCancelledEvent) {
        cancelledEvents.add(event)
    }

    override suspend fun publish(event: StockReturnedEvent) {
        stockReturnedEvents.add(event)
    }

    override suspend fun publish(event: OrderStatusChangedEvent) {
        statusChangedEvents.add(event)
    }
}

class InMemoryFulfillmentEventPublisher : FulfillmentEventPublisher {
    val statusChangedEvents = mutableListOf<OrderStatusChangedEvent>()
    val awaitingValidationEvents = mutableListOf<OrderAwaitingValidationEvent>()
    val stockConfirmedEvents = mutableListOf<OrderStockConfirmedEvent>()
    val cancelledEvents = mutableListOf<OrderCancelledEvent>()
    val stockReturnedEvents = mutableListOf<StockReturnedEvent>()

    override suspend fun publish(event: OrderStatusChangedEvent) {
        statusChangedEvents.add(event)
    }

    override suspend fun publish(event: OrderAwaitingValidationEvent) {
        awaitingValidationEvents.add(event)
    }

    override suspend fun publish(event: OrderStockConfirmedEvent) {
        stockConfirmedEvents.add(event)
    }

    override suspend fun publish(event: OrderCancelledEvent) {
        cancelledEvents.add(event)
    }

    override suspend fun publish(event: StockReturnedEvent) {
        stockReturnedEvents.add(event)
    }
}

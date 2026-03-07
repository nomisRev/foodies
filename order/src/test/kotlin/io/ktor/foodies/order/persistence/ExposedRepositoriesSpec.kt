package io.ktor.foodies.order.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.TestFixture
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.shared.TestRegistering
import io.ktor.foodies.events.common.CardBrand
import io.ktor.foodies.events.order.OrderStatus
import io.ktor.foodies.order.Address
import io.ktor.foodies.order.placement.CreateOrder
import io.ktor.foodies.order.placement.CreateOrderItem
import io.ktor.foodies.order.placement.ExposedPlacementRepository
import io.ktor.foodies.order.placement.PaymentDetails
import io.ktor.foodies.order.tracking.ExposedTrackingRepository
import io.ktor.foodies.server.test.PostgreSQLContainer
import io.ktor.foodies.server.test.ctxSuite
import io.ktor.foodies.server.test.postgresContainer
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

data class PersistenceContext(
    val postgres: TestFixture<PostgreSQLContainer>,
)

fun TestSuite.persistenceContext(): PersistenceContext = PersistenceContext(postgresContainer())

@TestRegistering
context(ctx: PersistenceContext)
fun TestSuite.testWithRepositories(
    name: String,
    block: suspend context(Test.ExecutionScope) (
        ExposedPlacementRepository,
        ExposedOrderRepository,
        ExposedTrackingRepository,
    ) -> Unit,
) {
    test(name) {
        val hikari = HikariDataSource(HikariConfig().apply {
            jdbcUrl = ctx.postgres().jdbcUrl
            username = ctx.postgres().username
            password = ctx.postgres().password
        })

        try {
            Flyway.configure().dataSource(hikari).load().migrate()
            val database = Database.connect(hikari)
            val placementRepository = ExposedPlacementRepository(database)
            val orderRepository = ExposedOrderRepository(database)
            val trackingRepository = ExposedTrackingRepository(database)

            transaction(database) {
                OrderHistory.deleteAll()
                OrderItems.deleteAll()
                Orders.deleteAll()
                PaymentMethods.deleteAll()
            }

            block(placementRepository, orderRepository, trackingRepository)
        } finally {
            hikari.close()
        }
    }
}

private fun createItem(
    menuItemId: Long,
    quantity: Int,
    unitPrice: String = "10.00",
) = CreateOrderItem(
    menuItemId = menuItemId,
    menuItemName = "Item-$menuItemId",
    pictureUrl = "https://cdn.test/item-$menuItemId.png",
    unitPrice = BigDecimal(unitPrice),
    quantity = quantity,
)

private fun createOrder(
    requestId: String,
    buyerId: String = "buyer-1",
    buyerEmail: String = "buyer-1@test.com",
    buyerName: String = "Buyer One",
    cardNumber: String = "5555444433334242",
    items: List<CreateOrderItem> = listOf(createItem(menuItemId = 1, quantity = 1)),
) = CreateOrder(
    requestId = requestId,
    buyerId = buyerId,
    buyerEmail = buyerEmail,
    buyerName = buyerName,
    deliveryAddress = Address("Street", "City", "State", "Country", "12345"),
    items = items,
    totalPrice = items.fold(BigDecimal.ZERO) { total, item ->
        total.add(item.unitPrice.multiply(BigDecimal(item.quantity)))
    },
    currency = "USD",
    paymentDetails = PaymentDetails(
        cardType = CardBrand.VISA,
        cardNumber = cardNumber,
        cardHolderName = "John Doe",
        cardSecurityNumber = "123",
        expirationMonth = 12,
        expirationYear = 2030,
    ),
)

val exposedRepositoriesSpec by ctxSuite(context = { persistenceContext() }) {
    testWithRepositories("should create order, find by request id, and reuse payment method") { placementRepository, _, _ ->
        val firstRequestId = UUID.randomUUID().toString()
        val firstOrder = placementRepository.create(
            createOrder(
                requestId = firstRequestId,
                items = listOf(createItem(menuItemId = 11, quantity = 2, unitPrice = "12.50")),
            )
        )
        val foundByRequestId = placementRepository.findByRequestId(firstRequestId)

        assertNotNull(foundByRequestId)
        assertEquals(firstOrder.id, foundByRequestId.id)
        assertEquals("4242", foundByRequestId.paymentMethod?.cardNumber)
        assertNull(placementRepository.findByRequestId("missing-request-id"))

        val secondOrder = placementRepository.create(
            createOrder(
                requestId = UUID.randomUUID().toString(),
                items = listOf(createItem(menuItemId = 12, quantity = 1, unitPrice = "8.00")),
            )
        )

        val firstPaymentMethod = firstOrder.paymentMethod
        val secondPaymentMethod = secondOrder.paymentMethod
        assertNotNull(firstPaymentMethod)
        assertNotNull(secondPaymentMethod)
        assertEquals(firstPaymentMethod.id, secondPaymentMethod.id)
    }

    testWithRepositories("should update order status, items, and history") { placementRepository, orderRepository, _ ->
        val created = placementRepository.create(
            createOrder(
                requestId = UUID.randomUUID().toString(),
                items = listOf(
                    createItem(menuItemId = 21, quantity = 2, unitPrice = "10.00"),
                    createItem(menuItemId = 22, quantity = 1, unitPrice = "7.50"),
                ),
            )
        )

        val updatedOrder = created.copy(
            status = OrderStatus.AwaitingValidation,
            description = "Stock check in progress",
            items = listOf(
                created.items.first().copy(
                    quantity = 3,
                    unitPrice = BigDecimal("11.00"),
                    discount = BigDecimal("1.25"),
                )
            ),
            updatedAt = Instant.fromEpochMilliseconds(System.currentTimeMillis()),
        )

        val persisted = orderRepository.update(updatedOrder)
        val loaded = orderRepository.findById(persisted.id)

        assertEquals(OrderStatus.AwaitingValidation, persisted.status)
        assertEquals("Stock check in progress", persisted.description)
        assertEquals(1, persisted.items.size)
        assertEquals(3, persisted.items.single().quantity)
        assertTrue(persisted.items.single().discount.compareTo(BigDecimal("1.25")) == 0)
        assertEquals(2, persisted.history.size)
        assertEquals(OrderStatus.Submitted, persisted.history.first().status)
        assertEquals(OrderStatus.AwaitingValidation, persisted.history.last().status)
        assertNotNull(loaded)
        assertEquals(1, loaded.items.size)
        assertEquals(OrderStatus.AwaitingValidation, loaded.status)
        assertNull(orderRepository.findById(Long.MAX_VALUE))
    }

    testWithRepositories("should paginate and filter order summaries") { placementRepository, orderRepository, trackingRepository ->
        val submittedOrder = placementRepository.create(
            createOrder(
                requestId = UUID.randomUUID().toString(),
                buyerId = "buyer-1",
                items = listOf(createItem(menuItemId = 31, quantity = 2, unitPrice = "6.00")),
            )
        )
        val toCancel = placementRepository.create(
            createOrder(
                requestId = UUID.randomUUID().toString(),
                buyerId = "buyer-1",
                items = listOf(
                    createItem(menuItemId = 32, quantity = 1, unitPrice = "4.00"),
                    createItem(menuItemId = 33, quantity = 3, unitPrice = "2.00"),
                ),
            )
        )
        val toPay = placementRepository.create(
            createOrder(
                requestId = UUID.randomUUID().toString(),
                buyerId = "buyer-2",
                buyerEmail = "buyer-2@test.com",
                buyerName = "Buyer Two",
                items = listOf(createItem(menuItemId = 34, quantity = 1, unitPrice = "20.00")),
            )
        )

        orderRepository.update(
            toCancel.copy(
                status = OrderStatus.Cancelled,
                description = "Cancelled by admin",
                updatedAt = Instant.fromEpochMilliseconds(System.currentTimeMillis()),
            )
        )
        orderRepository.update(
            toPay.copy(
                status = OrderStatus.Paid,
                description = "Payment received",
                updatedAt = Instant.fromEpochMilliseconds(System.currentTimeMillis()),
            )
        )

        val buyerOrders = trackingRepository.findByBuyerId("buyer-1", offset = 0, limit = 10, status = null)
        val pagedBuyerOrders = trackingRepository.findAll(offset = 1, limit = 1, status = null, buyerId = "buyer-1")
        val cancelledOrders = trackingRepository.findAll(
            offset = 0,
            limit = 10,
            status = OrderStatus.Cancelled,
            buyerId = null,
        )
        val submittedBuyerOrders = trackingRepository.findAll(
            offset = 0,
            limit = 10,
            status = OrderStatus.Submitted,
            buyerId = "buyer-1",
        )

        assertEquals(2, buyerOrders.total)
        assertEquals(2, buyerOrders.orders.size)
        assertEquals(2, buyerOrders.orders.first { it.id == submittedOrder.id }.itemCount)
        assertEquals(2, pagedBuyerOrders.total)
        assertEquals(1, pagedBuyerOrders.orders.size)
        assertEquals(1, cancelledOrders.total)
        assertEquals(toCancel.id, cancelledOrders.orders.single().id)
        assertEquals(1, submittedBuyerOrders.total)
        assertEquals(submittedOrder.id, submittedBuyerOrders.orders.single().id)
    }
}

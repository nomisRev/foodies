package io.ktor.foodies.payment

import io.ktor.foodies.server.test.PostgreSQLContainer
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import java.math.BigDecimal
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

class PaymentRepositoryTest {

    private val postgres = PostgreSQLContainer()
    private lateinit var database: Database
    private lateinit var repository: PostgresPaymentRepository

    @BeforeTest
    fun setup() {
        postgres.start()
        val ds = com.zaxxer.hikari.HikariDataSource(com.zaxxer.hikari.HikariConfig().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
        })
        
        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .load()
            .migrate()
            
        database = Database.connect(ds)
        repository = PostgresPaymentRepository(database)
    }

    @AfterTest
    fun tearDown() {
        postgres.stop()
    }

    @Test
    fun `should create and find payment record`() {
        val payment = createTestPayment(orderId = 1L)

        val created = repository.create(payment)
        assertTrue(created.id > 0)

        val found = repository.findById(created.id)
        assertNotNull(found)
        assertEquals(payment.orderId, found.orderId)
        assertEquals(payment.buyerId, found.buyerId)
        assertEquals(payment.amount.stripTrailingZeros(), found.amount.stripTrailingZeros())
        assertEquals(PaymentStatus.PENDING, found.status)
    }

    @Test
    fun `should find payment by order id`() {
        val orderId = 2L
        val payment = createTestPayment(orderId = orderId)
        repository.create(payment)
        
        val found = repository.findByOrderId(orderId)
        assertNotNull(found)
        assertEquals(orderId, found.orderId)
    }

    @Test
    fun `should update payment status`() {
        val payment = createTestPayment(orderId = 3L)
        val created = repository.create(payment)
        val processedAt = Clock.System.now()
        val transactionId = "txn-abc-123"
        
        val updated = repository.updateStatus(
            paymentId = created.id,
            status = PaymentStatus.SUCCEEDED,
            transactionId = transactionId,
            processedAt = processedAt
        )
        
        assertTrue(updated)
        
        val found = repository.findById(created.id)
        assertNotNull(found)
        assertEquals(PaymentStatus.SUCCEEDED, found.status)
        assertEquals(transactionId, found.transactionId)
        assertNotNull(found.processedAt)
        // Check if timestamps are close enough (ignoring nanos precision diff if any)
        assertTrue(Math.abs(found.processedAt!!.toEpochMilliseconds() - processedAt.toEpochMilliseconds()) < 1000)
    }
    
    @Test
    fun `should find by buyer id with pagination`() {
        val buyerId = "buyer-999"
        repeat(5) { i ->
            repository.create(createTestPayment(orderId = 100L + i, buyerId = buyerId))
        }
        
        val all = repository.findByBuyerId(buyerId, limit = 10)
        assertEquals(5, all.size)
        
        val paginated = repository.findByBuyerId(buyerId, limit = 2, offset = 2)
        assertEquals(2, paginated.size)
    }

    private fun createTestPayment(orderId: Long, buyerId: String = "user-123") = PaymentRecord(
        id = 0,
        orderId = orderId,
        buyerId = buyerId,
        amount = BigDecimal("100.00"),
        currency = "USD",
        status = PaymentStatus.PENDING,
        paymentMethod = PaymentMethodInfo(
            type = PaymentMethodType.CREDIT_CARD,
            cardLastFour = "4242",
            cardBrand = CardBrand.VISA,
            cardHolderName = "John Doe",
            expirationMonth = 12,
            expirationYear = 2025
        ),
        transactionId = null,
        failureReason = null,
        createdAt = Clock.System.now(),
        processedAt = null
    )
}

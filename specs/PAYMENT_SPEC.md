# Payment Service Specification

## Overview

The Payment Service is an event-driven microservice responsible for processing payments in the Foodies application. It follows the saga pattern, consuming order events and publishing payment results to coordinate the distributed transaction between the Ordering and Payment bounded contexts.

This specification is inspired by the [eShop PaymentProcessor](https://github.com/dotnet/eShop) architecture, adapted for the Foodies Kotlin/Ktor technology stack.

## Architecture

### Service Details

| Property | Value |
|----------|-------|
| Port | 8085 |
| Module | `payment` |
| Package | `io.ktor.foodies.payment` |
| Data Store | PostgreSQL (payment records) |
| Message Broker | RabbitMQ |
| Protocol | Event-driven (no REST API for payment processing) |

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Payment Flow                                   │
└─────────────────────────────────────────────────────────────────────────┘

                         ┌─────────────┐
                         │   Ordering  │
                         │   Service   │
                         └──────┬──────┘
                                │
                    OrderStockConfirmedEvent
                                │
                                ▼
                    ┌───────────────────────┐
                    │      RabbitMQ         │
                    │  payment.stock-confirmed │
                    └───────────┬───────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        Payment Service                                   │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                    Event Handler                                  │  │
│  │  OrderStockConfirmedEventHandler                                  │  │
│  │      │                                                            │  │
│  │      ▼                                                            │  │
│  │  PaymentService.processPayment()                                  │  │
│  │      │                                                            │  │
│  │      ├─── Validate payment method                                 │  │
│  │      ├─── Process with payment gateway (simulated)                │  │
│  │      ├─── Record payment in database                              │  │
│  │      │                                                            │  │
│  │      ▼                                                            │  │
│  │  Publish result event                                             │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                │                                        │
│                     ┌──────────┴──────────┐                            │
│                     │                     │                            │
│           PaymentSucceeded      PaymentFailed                          │
│                     │                     │                            │
└─────────────────────┼─────────────────────┼────────────────────────────┘
                      │                     │
                      ▼                     ▼
              ┌─────────────┐       ┌─────────────┐
              │  RabbitMQ   │       │  RabbitMQ   │
              │   payment   │       │   payment   │
              │  .succeeded │       │   .failed   │
              └──────┬──────┘       └──────┬──────┘
                     │                     │
                     ▼                     ▼
              ┌─────────────┐       ┌─────────────┐
              │  Ordering   │       │  Ordering   │
              │SetPaidStatus│       │CancelOrder  │
              └─────────────┘       └─────────────┘
```

### Order Status Flow with Payment

```
Order Created
      │
      ▼
Submitted ──────────────────────────────────────────┐
      │                                              │
      ▼                                              │
AwaitingValidation (stock check)                    │
      │                                              │
      ▼                                              │
StockConfirmed ─────────────────────────────────────┤
      │                                              │
      │ OrderStockConfirmedEvent                     │
      │ (consumed by Payment Service)               │
      ▼                                              │
[Payment Processing]                                 │
      │                                              │
      ├─── Success ───▶ Paid ───▶ Shipped           │
      │                                              │
      └─── Failure ───▶ Cancelled ◀─────────────────┘
```

## Data Model

### PaymentRecord

Stores payment transaction history for auditing and reconciliation.

```kotlin
@Serializable
data class PaymentRecord(
    val id: Long,
    val orderId: Long,
    val buyerId: String,                          // Keycloak user ID
    val amount: SerializableBigDecimal,
    val currency: String,                          // ISO 4217 currency code
    val status: PaymentStatus,
    val paymentMethod: PaymentMethodInfo,
    val transactionId: String?,                    // External payment gateway reference
    val failureReason: String?,                    // Reason for failure if status is FAILED
    val createdAt: Instant,
    val processedAt: Instant?
)
```

### PaymentStatus

```kotlin
@Serializable
enum class PaymentStatus {
    PENDING,      // Payment initiated, not yet processed
    PROCESSING,   // Payment being processed by gateway
    SUCCEEDED,    // Payment completed successfully
    FAILED,       // Payment failed
    REFUNDED      // Payment was refunded (future use)
}
```

### PaymentMethodInfo

Captures payment method details (tokenized, never stores raw card data).

```kotlin
@Serializable
data class PaymentMethodInfo(
    val type: PaymentMethodType,
    val cardLastFour: String?,                     // Last 4 digits only
    val cardBrand: CardBrand?,                     // Visa, MasterCard, etc.
    val cardHolderName: String?,
    val expirationMonth: Int?,
    val expirationYear: Int?
)

@Serializable
enum class PaymentMethodType {
    CREDIT_CARD,
    DEBIT_CARD,
    DIGITAL_WALLET,                                // Apple Pay, Google Pay (future)
    BANK_TRANSFER                                  // Future use
}

@Serializable
enum class CardBrand {
    VISA,
    MASTERCARD,
    AMEX,
    DISCOVER,
    UNKNOWN
}
```

## Event Integration

### Consumed Events

#### OrderStockConfirmedEvent

Triggers payment processing when stock has been confirmed for an order.

```kotlin
@Serializable
data class OrderStockConfirmedEvent(
    val eventId: String,                           // Idempotency key
    val orderId: Long,
    val buyerId: String,
    val totalAmount: SerializableBigDecimal,
    val currency: String,
    val paymentMethod: PaymentMethodInfo,
    val occurredAt: Instant
)
```

**RabbitMQ Configuration**
- Queue: `payment.stock-confirmed`
- Exchange: `foodies.events`
- Routing Key: `order.stock-confirmed`

### Published Events

#### OrderPaymentSucceededEvent

Published when payment is processed successfully.

```kotlin
@Serializable
data class OrderPaymentSucceededEvent(
    val eventId: String,
    val orderId: Long,
    val paymentId: Long,
    val transactionId: String,
    val amount: SerializableBigDecimal,
    val currency: String,
    val processedAt: Instant
)
```

**RabbitMQ Configuration**
- Exchange: `foodies.events`
- Routing Key: `payment.succeeded`

#### OrderPaymentFailedEvent

Published when payment processing fails.

```kotlin
@Serializable
data class OrderPaymentFailedEvent(
    val eventId: String,
    val orderId: Long,
    val failureReason: String,
    val failureCode: PaymentFailureCode,
    val occurredAt: Instant
)

@Serializable
enum class PaymentFailureCode {
    INSUFFICIENT_FUNDS,
    CARD_DECLINED,
    CARD_EXPIRED,
    INVALID_CARD,
    FRAUD_SUSPECTED,
    GATEWAY_ERROR,
    TIMEOUT,
    UNKNOWN
}
```

**RabbitMQ Configuration**
- Exchange: `foodies.events`
- Routing Key: `payment.failed`

## Service Layer

### PaymentService Interface

```kotlin
interface PaymentService {
    /**
     * Process a payment for an order.
     * This is idempotent - processing the same orderId multiple times
     * returns the existing result without re-charging.
     */
    suspend fun processPayment(request: ProcessPaymentRequest): PaymentResult

    /**
     * Get payment record by order ID.
     */
    suspend fun getPaymentByOrderId(orderId: Long): PaymentRecord?

    /**
     * Get payment record by payment ID.
     */
    suspend fun getPaymentById(paymentId: Long): PaymentRecord?
}
```

### ProcessPaymentRequest

```kotlin
data class ProcessPaymentRequest(
    val eventId: String,                           // For idempotency
    val orderId: Long,
    val buyerId: String,
    val amount: BigDecimal,
    val currency: String,
    val paymentMethod: PaymentMethodInfo
)
```

### PaymentResult

```kotlin
sealed interface PaymentResult {
    data class Success(
        val paymentId: Long,
        val transactionId: String,
        val processedAt: Instant
    ) : PaymentResult

    data class Failed(
        val reason: String,
        val code: PaymentFailureCode
    ) : PaymentResult

    data class AlreadyProcessed(
        val paymentRecord: PaymentRecord
    ) : PaymentResult
}
```

### PaymentServiceImpl

```kotlin
class PaymentServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val paymentGateway: PaymentGateway,
    private val clock: Clock = Clock.System
) : PaymentService {

    override suspend fun processPayment(request: ProcessPaymentRequest): PaymentResult {
        // Check for existing payment (idempotency)
        val existing = paymentRepository.findByOrderId(request.orderId)
        if (existing != null) {
            return PaymentResult.AlreadyProcessed(existing)
        }

        // Create pending payment record
        val pendingPayment = paymentRepository.create(
            PaymentRecord(
                id = 0,  // Auto-generated
                orderId = request.orderId,
                buyerId = request.buyerId,
                amount = request.amount.toSerializable(),
                currency = request.currency,
                status = PaymentStatus.PENDING,
                paymentMethod = request.paymentMethod,
                transactionId = null,
                failureReason = null,
                createdAt = clock.now(),
                processedAt = null
            )
        )

        // Process with payment gateway
        return try {
            val gatewayResult = paymentGateway.charge(
                ChargeRequest(
                    amount = request.amount,
                    currency = request.currency,
                    paymentMethod = request.paymentMethod,
                    orderId = request.orderId,
                    buyerId = request.buyerId
                )
            )

            when (gatewayResult) {
                is GatewayResult.Success -> {
                    val processedAt = clock.now()
                    paymentRepository.updateStatus(
                        paymentId = pendingPayment.id,
                        status = PaymentStatus.SUCCEEDED,
                        transactionId = gatewayResult.transactionId,
                        processedAt = processedAt
                    )
                    PaymentResult.Success(
                        paymentId = pendingPayment.id,
                        transactionId = gatewayResult.transactionId,
                        processedAt = processedAt
                    )
                }
                is GatewayResult.Failed -> {
                    paymentRepository.updateStatus(
                        paymentId = pendingPayment.id,
                        status = PaymentStatus.FAILED,
                        failureReason = gatewayResult.reason
                    )
                    PaymentResult.Failed(
                        reason = gatewayResult.reason,
                        code = gatewayResult.code
                    )
                }
            }
        } catch (e: Exception) {
            paymentRepository.updateStatus(
                paymentId = pendingPayment.id,
                status = PaymentStatus.FAILED,
                failureReason = "Gateway error: ${e.message}"
            )
            PaymentResult.Failed(
                reason = "Payment gateway unavailable",
                code = PaymentFailureCode.GATEWAY_ERROR
            )
        }
    }
}
```

## Payment Gateway

### Interface

```kotlin
interface PaymentGateway {
    suspend fun charge(request: ChargeRequest): GatewayResult
}

data class ChargeRequest(
    val amount: BigDecimal,
    val currency: String,
    val paymentMethod: PaymentMethodInfo,
    val orderId: Long,
    val buyerId: String
)

sealed interface GatewayResult {
    data class Success(val transactionId: String) : GatewayResult
    data class Failed(val reason: String, val code: PaymentFailureCode) : GatewayResult
}
```

### Simulated Gateway (Development/Testing)

```kotlin
class SimulatedPaymentGateway(
    private val config: PaymentGatewayConfig
) : PaymentGateway {

    override suspend fun charge(request: ChargeRequest): GatewayResult {
        // Simulate processing delay
        delay(config.processingDelayMs)

        // Configurable success/failure for testing
        return if (config.alwaysSucceed) {
            GatewayResult.Success(
                transactionId = "txn_${UUID.randomUUID()}"
            )
        } else {
            // Simulate various failure scenarios based on card number patterns
            simulateCardBehavior(request)
        }
    }

    private fun simulateCardBehavior(request: ChargeRequest): GatewayResult {
        // Test card numbers for different scenarios
        return when {
            request.paymentMethod.cardLastFour == "0000" ->
                GatewayResult.Failed("Card declined", PaymentFailureCode.CARD_DECLINED)
            request.paymentMethod.cardLastFour == "1111" ->
                GatewayResult.Failed("Insufficient funds", PaymentFailureCode.INSUFFICIENT_FUNDS)
            request.paymentMethod.cardLastFour == "2222" ->
                GatewayResult.Failed("Card expired", PaymentFailureCode.CARD_EXPIRED)
            else ->
                GatewayResult.Success(transactionId = "txn_${UUID.randomUUID()}")
        }
    }
}

@Serializable
data class PaymentGatewayConfig(
    @SerialName("always_succeed") val alwaysSucceed: Boolean = true,
    @SerialName("processing_delay_ms") val processingDelayMs: Long = 100
)
```

## Event Handler

### OrderStockConfirmedEventHandler

```kotlin
class OrderStockConfirmedEventHandler(
    private val paymentService: PaymentService,
    private val eventPublisher: EventPublisher,
    private val logger: Logger = LoggerFactory.getLogger(OrderStockConfirmedEventHandler::class.java)
) {

    suspend fun handle(event: OrderStockConfirmedEvent) {
        logger.info("Processing payment for order ${event.orderId}")

        val result = paymentService.processPayment(
            ProcessPaymentRequest(
                eventId = event.eventId,
                orderId = event.orderId,
                buyerId = event.buyerId,
                amount = event.totalAmount.toBigDecimal(),
                currency = event.currency,
                paymentMethod = event.paymentMethod
            )
        )

        when (result) {
            is PaymentResult.Success -> {
                logger.info("Payment succeeded for order ${event.orderId}: ${result.transactionId}")
                eventPublisher.publish(
                    OrderPaymentSucceededEvent(
                        eventId = UUID.randomUUID().toString(),
                        orderId = event.orderId,
                        paymentId = result.paymentId,
                        transactionId = result.transactionId,
                        amount = event.totalAmount,
                        currency = event.currency,
                        processedAt = result.processedAt
                    )
                )
            }
            is PaymentResult.Failed -> {
                logger.warn("Payment failed for order ${event.orderId}: ${result.reason}")
                eventPublisher.publish(
                    OrderPaymentFailedEvent(
                        eventId = UUID.randomUUID().toString(),
                        orderId = event.orderId,
                        failureReason = result.reason,
                        failureCode = result.code,
                        occurredAt = Clock.System.now()
                    )
                )
            }
            is PaymentResult.AlreadyProcessed -> {
                logger.info("Payment already processed for order ${event.orderId}")
                // Re-publish the result for idempotent handling
                when (result.paymentRecord.status) {
                    PaymentStatus.SUCCEEDED -> eventPublisher.publish(
                        OrderPaymentSucceededEvent(
                            eventId = UUID.randomUUID().toString(),
                            orderId = event.orderId,
                            paymentId = result.paymentRecord.id,
                            transactionId = result.paymentRecord.transactionId!!,
                            amount = result.paymentRecord.amount,
                            currency = result.paymentRecord.currency,
                            processedAt = result.paymentRecord.processedAt!!
                        )
                    )
                    PaymentStatus.FAILED -> eventPublisher.publish(
                        OrderPaymentFailedEvent(
                            eventId = UUID.randomUUID().toString(),
                            orderId = event.orderId,
                            failureReason = result.paymentRecord.failureReason ?: "Unknown",
                            failureCode = PaymentFailureCode.UNKNOWN,
                            occurredAt = Clock.System.now()
                        )
                    )
                    else -> { /* PENDING/PROCESSING - wait for completion */ }
                }
            }
        }
    }
}
```

## Repository Layer

### PaymentRepository Interface

```kotlin
interface PaymentRepository {
    suspend fun create(payment: PaymentRecord): PaymentRecord
    suspend fun findById(id: Long): PaymentRecord?
    suspend fun findByOrderId(orderId: Long): PaymentRecord?
    suspend fun findByBuyerId(buyerId: String, limit: Int = 50, offset: Int = 0): List<PaymentRecord>
    suspend fun updateStatus(
        paymentId: Long,
        status: PaymentStatus,
        transactionId: String? = null,
        failureReason: String? = null,
        processedAt: Instant? = null
    ): Boolean
}
```

### Database Schema

```sql
-- V1__create_payment_table.sql
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    buyer_id VARCHAR(255) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payment_method_type VARCHAR(20) NOT NULL,
    card_last_four VARCHAR(4),
    card_brand VARCHAR(20),
    card_holder_name VARCHAR(200),
    expiration_month INT,
    expiration_year INT,
    transaction_id VARCHAR(255),
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ
);

CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_buyer_id ON payments(buyer_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_created_at ON payments(created_at);
```

### Exposed Table Definition

```kotlin
object PaymentsTable : LongIdTable("payments") {
    val orderId = long("order_id").uniqueIndex()
    val buyerId = varchar("buyer_id", 255)
    val amount = decimal("amount", 12, 2)
    val currency = varchar("currency", 3).default("USD")
    val status = varchar("status", 20).default("PENDING")
    val paymentMethodType = varchar("payment_method_type", 20)
    val cardLastFour = varchar("card_last_four", 4).nullable()
    val cardBrand = varchar("card_brand", 20).nullable()
    val cardHolderName = varchar("card_holder_name", 200).nullable()
    val expirationMonth = integer("expiration_month").nullable()
    val expirationYear = integer("expiration_year").nullable()
    val transactionId = varchar("transaction_id", 255).nullable()
    val failureReason = text("failure_reason").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val processedAt = timestampWithTimeZone("processed_at").nullable()
}
```

### PostgresPaymentRepository

```kotlin
class PostgresPaymentRepository(
    private val database: Database
) : PaymentRepository {

    override suspend fun create(payment: PaymentRecord): PaymentRecord = dbQuery {
        val id = PaymentsTable.insertAndGetId {
            it[orderId] = payment.orderId
            it[buyerId] = payment.buyerId
            it[amount] = payment.amount.toBigDecimal()
            it[currency] = payment.currency
            it[status] = payment.status.name
            it[paymentMethodType] = payment.paymentMethod.type.name
            it[cardLastFour] = payment.paymentMethod.cardLastFour
            it[cardBrand] = payment.paymentMethod.cardBrand?.name
            it[cardHolderName] = payment.paymentMethod.cardHolderName
            it[expirationMonth] = payment.paymentMethod.expirationMonth
            it[expirationYear] = payment.paymentMethod.expirationYear
        }
        payment.copy(id = id.value)
    }

    override suspend fun findByOrderId(orderId: Long): PaymentRecord? = dbQuery {
        PaymentsTable.selectAll()
            .where { PaymentsTable.orderId eq orderId }
            .map { it.toPaymentRecord() }
            .singleOrNull()
    }

    override suspend fun updateStatus(
        paymentId: Long,
        status: PaymentStatus,
        transactionId: String?,
        failureReason: String?,
        processedAt: Instant?
    ): Boolean = dbQuery {
        PaymentsTable.update({ PaymentsTable.id eq paymentId }) {
            it[PaymentsTable.status] = status.name
            transactionId?.let { txn -> it[PaymentsTable.transactionId] = txn }
            failureReason?.let { reason -> it[PaymentsTable.failureReason] = reason }
            processedAt?.let { time ->
                it[PaymentsTable.processedAt] = time.toLocalDateTime(TimeZone.UTC)
                    .toJavaLocalDateTime()
                    .atOffset(java.time.ZoneOffset.UTC)
            }
        } > 0
    }

    private fun ResultRow.toPaymentRecord() = PaymentRecord(
        id = this[PaymentsTable.id].value,
        orderId = this[PaymentsTable.orderId],
        buyerId = this[PaymentsTable.buyerId],
        amount = this[PaymentsTable.amount].toSerializable(),
        currency = this[PaymentsTable.currency],
        status = PaymentStatus.valueOf(this[PaymentsTable.status]),
        paymentMethod = PaymentMethodInfo(
            type = PaymentMethodType.valueOf(this[PaymentsTable.paymentMethodType]),
            cardLastFour = this[PaymentsTable.cardLastFour],
            cardBrand = this[PaymentsTable.cardBrand]?.let { CardBrand.valueOf(it) },
            cardHolderName = this[PaymentsTable.cardHolderName],
            expirationMonth = this[PaymentsTable.expirationMonth],
            expirationYear = this[PaymentsTable.expirationYear]
        ),
        transactionId = this[PaymentsTable.transactionId],
        failureReason = this[PaymentsTable.failureReason],
        createdAt = this[PaymentsTable.createdAt].toKotlinInstant(),
        processedAt = this[PaymentsTable.processedAt]?.toKotlinInstant()
    )

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, database) { block() }
}
```

## API Endpoints (Admin/Monitoring Only)

The Payment Service is primarily event-driven, but exposes limited REST endpoints for monitoring and admin purposes.

### GET /healthz

Basic health check endpoint.

**Response**
- `200 OK`: Service is running

### GET /healthz/ready

Readiness check including database and RabbitMQ connectivity.

**Response**
- `200 OK`: All dependencies healthy
- `503 Service Unavailable`: One or more dependencies unhealthy

```json
{
  "status": "UP",
  "checks": {
    "database": "UP",
    "rabbitmq": "UP"
  }
}
```

### GET /payments/{orderId} (Admin)

Get payment details by order ID. Requires admin authentication.

**Response**
- `200 OK`: Returns payment record
- `401 Unauthorized`: Missing or invalid token
- `403 Forbidden`: Non-admin user
- `404 Not Found`: Payment not found

```json
{
  "id": 1,
  "orderId": 12345,
  "buyerId": "user-uuid-123",
  "amount": "49.99",
  "currency": "USD",
  "status": "SUCCEEDED",
  "paymentMethod": {
    "type": "CREDIT_CARD",
    "cardLastFour": "4242",
    "cardBrand": "VISA",
    "cardHolderName": "John Doe",
    "expirationMonth": 12,
    "expirationYear": 2025
  },
  "transactionId": "txn_abc123",
  "failureReason": null,
  "createdAt": "2024-01-15T10:30:00Z",
  "processedAt": "2024-01-15T10:30:01Z"
}
```

## Project Structure

```
payment/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── io/ktor/foodies/payment/
│   │   │       ├── App.kt                    # Application entry point
│   │   │       ├── Config.kt                 # Configuration classes
│   │   │       ├── Domain.kt                 # Data models
│   │   │       ├── Routes.kt                 # HTTP route definitions (admin)
│   │   │       ├── Service.kt                # Payment service implementation
│   │   │       ├── Repository.kt             # PostgreSQL repository
│   │   │       ├── PaymentModule.kt          # DI module
│   │   │       ├── gateway/
│   │   │       │   ├── PaymentGateway.kt     # Gateway interface
│   │   │       │   └── SimulatedGateway.kt   # Development gateway
│   │   │       └── events/
│   │   │           ├── Events.kt             # Event definitions
│   │   │           ├── EventPublisher.kt     # RabbitMQ publisher
│   │   │           └── OrderStockConfirmedHandler.kt
│   │   └── resources/
│   │       ├── application.yaml
│   │       └── db/migration/
│   │           └── V1__create_payment_table.sql
│   └── test/
│       └── kotlin/
│           └── io/ktor/foodies/payment/
│               ├── PaymentServiceTest.kt
│               ├── PaymentRepositoryTest.kt
│               ├── OrderStockConfirmedHandlerTest.kt
│               └── SimulatedGatewayTest.kt
├── build.gradle.kts
└── README.md
```

## Configuration

### application.yaml

```yaml
config:
  host: "$HOST:0.0.0.0"
  port: "$PORT:8085"
  data_source:
    url: "$DB_URL:jdbc:postgresql://localhost:5434/foodies-payment-database"
    username: "$DB_USERNAME:foodies_admin"
    password: "$DB_PASSWORD:foodies_password"
  rabbit:
    host: "$RABBITMQ_HOST:localhost"
    port: "$RABBITMQ_PORT:5672"
    username: "$RABBITMQ_USERNAME:guest"
    password: "$RABBITMQ_PASSWORD:guest"
    consume_queue: "$RABBITMQ_CONSUME_QUEUE:payment.stock-confirmed"
    publish_exchange: "$RABBITMQ_PUBLISH_EXCHANGE:foodies.events"
  gateway:
    always_succeed: "$PAYMENT_ALWAYS_SUCCEED:true"
    processing_delay_ms: "$PAYMENT_DELAY_MS:100"
```

### Config.kt

```kotlin
package io.ktor.foodies.payment

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val host: String,
    val port: Int,
    @SerialName("data_source") val dataSource: DataSourceConfig,
    val rabbit: RabbitConfig,
    val gateway: PaymentGatewayConfig,
)

@Serializable
data class DataSourceConfig(
    val url: String,
    val username: String,
    val password: String,
)

@Serializable
data class RabbitConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    @SerialName("consume_queue") val consumeQueue: String,
    @SerialName("publish_exchange") val publishExchange: String,
)

@Serializable
data class PaymentGatewayConfig(
    @SerialName("always_succeed") val alwaysSucceed: Boolean,
    @SerialName("processing_delay_ms") val processingDelayMs: Long,
)
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| HOST | Server bind host | 0.0.0.0 |
| PORT | Server port | 8085 |
| DB_URL | PostgreSQL connection URL | jdbc:postgresql://localhost:5434/foodies-payment-database |
| DB_USERNAME | Database username | foodies_admin |
| DB_PASSWORD | Database password | foodies_password |
| RABBITMQ_HOST | RabbitMQ host | localhost |
| RABBITMQ_PORT | RabbitMQ port | 5672 |
| RABBITMQ_USERNAME | RabbitMQ username | guest |
| RABBITMQ_PASSWORD | RabbitMQ password | guest |
| RABBITMQ_CONSUME_QUEUE | Queue to consume events from | payment.stock-confirmed |
| RABBITMQ_PUBLISH_EXCHANGE | Exchange to publish events to | foodies.events |
| PAYMENT_ALWAYS_SUCCEED | Simulated gateway always succeeds | true |
| PAYMENT_DELAY_MS | Simulated processing delay | 100 |

## Dependency Module

### PaymentModule.kt

```kotlin
package io.ktor.foodies.payment

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database

class PaymentModule(
    val dataSource: HikariDataSource,
    val database: Database,
    val paymentRepository: PaymentRepository,
    val paymentGateway: PaymentGateway,
    val paymentService: PaymentService,
    val eventPublisher: EventPublisher,
    val eventHandler: OrderStockConfirmedEventHandler,
    val eventConsumer: RabbitMQEventConsumer
)

fun Application.paymentModule(config: Config): PaymentModule {
    val dataSource = hikariDataSource(config.dataSource)
    val database = Database.connect(dataSource)

    // Run migrations
    Flyway.configure()
        .dataSource(dataSource)
        .load()
        .migrate()

    val paymentRepository = PostgresPaymentRepository(database)
    val paymentGateway = SimulatedPaymentGateway(config.gateway)
    val paymentService = PaymentServiceImpl(paymentRepository, paymentGateway)
    val eventPublisher = RabbitMQEventPublisher(config.rabbit)
    val eventHandler = OrderStockConfirmedEventHandler(paymentService, eventPublisher)
    val eventConsumer = RabbitMQEventConsumer(config.rabbit, eventHandler)

    // Register shutdown hooks
    monitor.subscribe(ApplicationStopped) {
        eventConsumer.close()
        eventPublisher.close()
        dataSource.close()
    }

    // Start consuming events
    eventConsumer.start()

    return PaymentModule(
        dataSource = dataSource,
        database = database,
        paymentRepository = paymentRepository,
        paymentGateway = paymentGateway,
        paymentService = paymentService,
        eventPublisher = eventPublisher,
        eventHandler = eventHandler,
        eventConsumer = eventConsumer
    )
}

private fun hikariDataSource(config: DataSourceConfig): HikariDataSource {
    return HikariDataSource(HikariConfig().apply {
        jdbcUrl = config.url
        username = config.username
        password = config.password
        maximumPoolSize = 10
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
    })
}
```

## Application Entry Point

### App.kt

```kotlin
package io.ktor.foodies.payment

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main() {
    val config = ApplicationConfig("application.yaml").config("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        app(paymentModule(config))
    }.start(wait = true)
}

fun Application.app(module: PaymentModule) {
    install(ContentNegotiation) { json() }

    routing {
        healthz()
        healthzReady(module)
        paymentRoutes(module.paymentService)
    }
}

fun Route.healthz() = get("/healthz") {
    call.respond(HttpStatusCode.OK)
}

fun Route.healthzReady(module: PaymentModule) = get("/healthz/ready") {
    val dbHealthy = runCatching {
        module.database.connector().connection.isValid(1)
    }.getOrDefault(false)

    val status = if (dbHealthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
    call.respond(status, mapOf(
        "status" to if (dbHealthy) "UP" else "DOWN",
        "checks" to mapOf("database" to if (dbHealthy) "UP" else "DOWN")
    ))
}

fun Route.paymentRoutes(paymentService: PaymentService) {
    // Admin endpoint - would require authentication in production
    get("/payments/{orderId}") {
        val orderId = call.parameters["orderId"]?.toLongOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid order ID")

        val payment = paymentService.getPaymentByOrderId(orderId)
            ?: return@get call.respond(HttpStatusCode.NotFound, "Payment not found")

        call.respond(payment)
    }
}
```

## Kubernetes Deployment

### PostgreSQL Deployment

```yaml
# k8s/databases/payment-database.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: payment-database
  namespace: foodies
spec:
  replicas: 1
  selector:
    matchLabels:
      app: payment-database
  template:
    metadata:
      labels:
        app: payment-database
    spec:
      containers:
      - name: postgres
        image: postgres:16-alpine
        ports:
        - containerPort: 5432
        env:
        - name: POSTGRES_DB
          value: "foodies-payment-database"
        - name: POSTGRES_USER
          valueFrom:
            secretKeyRef:
              name: payment-db-secret
              key: username
        - name: POSTGRES_PASSWORD
          valueFrom:
            secretKeyRef:
              name: payment-db-secret
              key: password
        resources:
          requests:
            memory: "256Mi"
            cpu: "100m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        volumeMounts:
        - name: payment-data
          mountPath: /var/lib/postgresql/data
      volumes:
      - name: payment-data
        persistentVolumeClaim:
          claimName: payment-database-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: payment-database
  namespace: foodies
spec:
  selector:
    app: payment-database
  ports:
  - port: 5432
    targetPort: 5432
```

### Payment Service Deployment

```yaml
# k8s/services/payment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: payment
  namespace: foodies
spec:
  replicas: 2
  selector:
    matchLabels:
      app: payment
  template:
    metadata:
      labels:
        app: payment
    spec:
      containers:
      - name: payment
        image: foodies-payment:latest
        ports:
        - containerPort: 8085
        env:
        - name: PORT
          value: "8085"
        - name: DB_URL
          value: "jdbc:postgresql://payment-database:5432/foodies-payment-database"
        - name: DB_USERNAME
          valueFrom:
            secretKeyRef:
              name: payment-db-secret
              key: username
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: payment-db-secret
              key: password
        - name: RABBITMQ_HOST
          value: "rabbitmq"
        - name: RABBITMQ_USERNAME
          valueFrom:
            secretKeyRef:
              name: rabbitmq-secret
              key: username
        - name: RABBITMQ_PASSWORD
          valueFrom:
            secretKeyRef:
              name: rabbitmq-secret
              key: password
        - name: PAYMENT_ALWAYS_SUCCEED
          value: "true"
        resources:
          requests:
            memory: "256Mi"
            cpu: "100m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        readinessProbe:
          httpGet:
            path: /healthz/ready
            port: 8085
          initialDelaySeconds: 10
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: /healthz
            port: 8085
          initialDelaySeconds: 15
          periodSeconds: 20
---
apiVersion: v1
kind: Service
metadata:
  name: payment
  namespace: foodies
spec:
  selector:
    app: payment
  ports:
  - port: 8085
    targetPort: 8085
```

## Dependencies

### build.gradle.kts

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.ktor.plugin")
    application
}

application {
    mainClass.set("io.ktor.foodies.payment.AppKt")
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-status-pages")

    // Database
    implementation("org.jetbrains.exposed:exposed-core")
    implementation("org.jetbrains.exposed:exposed-dao")
    implementation("org.jetbrains.exposed:exposed-jdbc")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime")
    implementation("org.postgresql:postgresql")
    implementation("com.zaxxer:HikariCP")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // RabbitMQ
    implementation("com.rabbitmq:amqp-client:5.20.0")

    // Shared modules
    implementation(project(":server-shared"))
    implementation(project(":keycloak-events"))

    // Logging
    implementation("ch.qos.logback:logback-classic")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    // Testing
    testImplementation(project(":server-shared-test"))
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:rabbitmq")
}
```

## Testing Strategy

### Unit Tests

1. **PaymentServiceTest**: Test payment processing logic
   - Successful payment processing
   - Failed payment processing
   - Idempotent payment handling
   - Gateway error handling

2. **SimulatedGatewayTest**: Test gateway behavior
   - Always succeed mode
   - Card number-based failure simulation

### Integration Tests

1. **PaymentRepositoryTest**: Test database operations with Testcontainers
   - Create payment record
   - Find by order ID
   - Update status
   - Idempotency constraints

2. **OrderStockConfirmedHandlerTest**: Test event handling
   - Successful payment flow
   - Failed payment flow
   - Idempotent event handling
   - Event publishing

### Example Test

```kotlin
class PaymentServiceTest {

    @Test
    fun `successful payment publishes success event`() = runTest {
        val repository = InMemoryPaymentRepository()
        val gateway = SimulatedPaymentGateway(PaymentGatewayConfig(alwaysSucceed = true))
        val service = PaymentServiceImpl(repository, gateway)

        val result = service.processPayment(
            ProcessPaymentRequest(
                eventId = "evt-123",
                orderId = 1L,
                buyerId = "user-123",
                amount = BigDecimal("49.99"),
                currency = "USD",
                paymentMethod = PaymentMethodInfo(
                    type = PaymentMethodType.CREDIT_CARD,
                    cardLastFour = "4242",
                    cardBrand = CardBrand.VISA,
                    cardHolderName = "John Doe",
                    expirationMonth = 12,
                    expirationYear = 2025
                )
            )
        )

        assertTrue(result is PaymentResult.Success)
        val payment = repository.findByOrderId(1L)
        assertNotNull(payment)
        assertEquals(PaymentStatus.SUCCEEDED, payment.status)
    }

    @Test
    fun `duplicate payment request returns already processed`() = runTest {
        val repository = InMemoryPaymentRepository()
        val gateway = SimulatedPaymentGateway(PaymentGatewayConfig(alwaysSucceed = true))
        val service = PaymentServiceImpl(repository, gateway)

        val request = ProcessPaymentRequest(
            eventId = "evt-123",
            orderId = 1L,
            buyerId = "user-123",
            amount = BigDecimal("49.99"),
            currency = "USD",
            paymentMethod = PaymentMethodInfo(
                type = PaymentMethodType.CREDIT_CARD,
                cardLastFour = "4242",
                cardBrand = CardBrand.VISA,
                cardHolderName = "John Doe",
                expirationMonth = 12,
                expirationYear = 2025
            )
        )

        // First call succeeds
        val first = service.processPayment(request)
        assertTrue(first is PaymentResult.Success)

        // Second call returns already processed
        val second = service.processPayment(request)
        assertTrue(second is PaymentResult.AlreadyProcessed)
    }
}
```

## Security Considerations

1. **No Raw Card Data Storage**: Only last 4 digits and tokenized references are stored
2. **PCI DSS Compliance**: In production, integrate with a PCI-compliant payment gateway
3. **Idempotency**: Prevent duplicate charges via order ID uniqueness
4. **Audit Trail**: All payment attempts are recorded in the database
5. **TLS**: All communication should use TLS in production
6. **Secret Management**: Use Kubernetes secrets for credentials
7. **Input Validation**: Validate all incoming event data
8. **Rate Limiting**: Consider rate limiting on admin endpoints

## Future Enhancements

1. **Real Payment Gateway Integration**: Stripe, PayPal, etc.
2. **Refund Support**: Handle refunds and partial refunds
3. **Retry Logic**: Automatic retry with exponential backoff for transient failures
4. **Webhook Support**: Receive payment gateway webhooks for async updates
5. **Multi-Currency**: Support for currency conversion
6. **Payment Method Tokenization**: Store tokenized payment methods for repeat customers
7. **3D Secure**: Support for 3D Secure authentication
8. **Fraud Detection**: Integration with fraud detection services
9. **Reporting**: Payment analytics and reconciliation reports

## API Summary

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | /healthz | Basic health check | None |
| GET | /healthz/ready | Readiness check | None |
| GET | /payments/{orderId} | Get payment by order ID | Admin |

## Event Summary

| Event | Direction | Description |
|-------|-----------|-------------|
| OrderStockConfirmedEvent | Consumed | Triggers payment processing |
| OrderPaymentSucceededEvent | Published | Payment completed successfully |
| OrderPaymentFailedEvent | Published | Payment failed |

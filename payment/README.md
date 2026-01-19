# Payment Service

Event-driven microservice that handles payment processing for the Foodies platform. Processes payments through a simulated payment gateway, maintains payment records in PostgreSQL, and publishes payment events via RabbitMQ.

> For common architectural patterns, testing approach, and build commands, see the [main README](../README.md#architecture-patterns).

## Architecture

### Core Components

- **PaymentService**: Main business logic for payment processing
- **PaymentRepository**: Data access layer using Exposed ORM
- **PaymentGateway**: Abstraction for payment provider integration
- **SimulatedPaymentGateway**: Mock gateway for testing with configurable behavior
- **EventPublisher**: Publishes payment events to RabbitMQ
- **Event Handlers**: Consumes order events to trigger payment processing

### Event Flow

1. Order service publishes `OrderStockConfirmedEvent`
2. Payment service consumes the event and processes payment
3. Payment service publishes either:
   - `OrderPaymentSucceededEvent` (on successful payment)
   - `OrderPaymentFailedEvent` (on payment failure)

### Database Schema

The `payments` table stores:
- Order and buyer information
- Payment amount and currency
- Payment method details (card information)
- Payment status and timestamps
- Transaction ID and failure reasons

## API Endpoints

### GET /payments/{orderId}
Retrieves payment information for a specific order.

**Response:** `PaymentRecord`

## Configuration

The service is configured via `application.yaml` with environment variable overrides:

### Database Configuration
```yaml
data_source:
  url: "jdbc:postgresql://localhost:5434/foodies-payment-database"
  username: "foodies_admin"
  password: "foodies_password"
```

### RabbitMQ Configuration
```yaml
rabbit:
  host: "localhost"
  port: 5672
  username: "guest"
  password: "guest"
  consume_queue: "payment.stock-confirmed"
  publish_exchange: "foodies.events"
```

### Payment Gateway Configuration
```yaml
gateway:
  always_succeed: true
  processing_delay_ms: 100
```

The simulated gateway supports test scenarios:
- Card ending in `0000`: Card declined
- Card ending in `1111`: Insufficient funds
- Card ending in `2222`: Card expired
- All other cards: Success

## Building and Running

### Build
```bash
./gradlew :payment:build
```

### Run Tests
```bash
./gradlew :payment:jvmTest
```

### Run Locally
```bash
./gradlew :payment:run
```

### Docker Image
```bash
./gradlew :payment:publishImageToLocalRegistry
```

## Deployment

### Kubernetes
The service is deployed using Kubernetes manifests in `k8s/base/payment/`:

- `deployment.yaml`: Service deployment with resource limits
- `service.yaml`: Service exposure configuration
- `database.yaml`: PostgreSQL database deployment
- `kustomization.yaml`: Kustomize configuration

### Health Checks
- `/healthz/startup`: Startup probe
- `/healthz/liveness`: Liveness probe
- `/healthz/readiness`: Readiness probe (includes database and RabbitMQ connectivity)

## Domain Models

### PaymentRecord
```kotlin
data class PaymentRecord(
    val id: Long,
    val orderId: Long,
    val buyerId: String,
    val amount: SerializableBigDecimal,
    val currency: String,
    val status: PaymentStatus,
    val paymentMethod: PaymentMethodInfo,
    val transactionId: String?,
    val failureReason: String?,
    val createdAt: Instant,
    val processedAt: Instant?
)
```

### PaymentStatus
- `PENDING`: Payment initiated, not yet processed
- `PROCESSING`: Payment being processed by gateway
- `SUCCEEDED`: Payment completed successfully
- `FAILED`: Payment failed
- `REFUNDED`: Payment was refunded (future use)

### PaymentResult
```kotlin
sealed interface PaymentResult {
    data class Success(val paymentId: Long, val transactionId: String, val processedAt: Instant) : PaymentResult
    data class Failed(val reason: String, val code: PaymentFailureCode) : PaymentResult
    data class AlreadyProcessed(val paymentRecord: PaymentRecord) : PaymentResult
}
```

## Testing

Test suites use TestContainers with real PostgreSQL and RabbitMQ:

- **PaymentServiceSpec**: Core payment processing logic
- **PaymentRepositorySpec**: Data access layer
- **SimulatedGatewaySpec**: Payment gateway behavior
- **PaymentEventPublisherSpec**: Event publishing
- **OrderStockConfirmedHandlerSpec**: Event handling

## Key Features

### Idempotency
- Payment processing is idempotent based on `orderId`
- Duplicate requests return existing payment records
- Event replay is supported through idempotent handling

### Error Handling
- Gateway failures are caught and recorded
- Payment failures include specific error codes
- Structured logging with tracing support

### Monitoring
- OpenTelemetry integration for distributed tracing
- Health checks for all external dependencies
- Structured logging for observability

## Development

### Adding New Payment Gateways
1. Implement `PaymentGateway` interface
2. Add configuration in `PaymentGatewayConfig`
3. Update `PaymentModule` to use new gateway
4. Add appropriate tests

### Event Extensions
1. Define new event types in `events-payment` module
2. Add publishers/ handlers as needed
3. Update event routing configuration

### Database Changes
1. Create new migration script in `src/main/resources/db/migration/`
2. Update `PaymentsTable` schema definition
3. Modify `PaymentRepository` and domain models
4. Update tests accordingly
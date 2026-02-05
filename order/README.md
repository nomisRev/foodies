# Order Service

Order management service backed by PostgreSQL. Handles order creation, tracking, cancellation, and payment integration with RabbitMQ event-driven architecture.

> For common architectural patterns, testing approach, and build commands, see the [main README](../README.md#architecture-patterns).

## Configuration

Values come from `config` in `src/main/resources/application.yaml` (env overrides shown in parentheses):

- `HOST` / `PORT`: bind address and port (default `0.0.0.0:8084`)
- `AUTH_ISSUER`: Keycloak OIDC issuer URL (default `http://localhost:8000/realms/foodies-keycloak`)
- `AUTH_AUDIENCE`: Expected audience/client ID for JWT validation (default `order-service`)
- `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`: PostgreSQL connection (defaults to `jdbc:postgresql://localhost:5433/foodies-order-database`, `foodies_admin/foodies_password`)
- `BASKET_BASE_URL`: Basket service base URL (default `http://localhost:8083`)
- `PAYMENT_BASE_URL`: Payment service base URL (default `http://localhost:8085`)
- `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD`: RabbitMQ connection (defaults to `localhost:5672`, `guest/guest`)
- `RABBITMQ_EXCHANGE` / `RABBITMQ_ROUTING_KEY`: RabbitMQ exchange and routing key (defaults to `foodies`, `order.created`)
- `ORDER_GRACE_PERIOD_SECONDS`: Grace period for order cancellation (default `60`)
- `OTEL_EXPORTER_OTLP_ENDPOINT`: OpenTelemetry endpoint (default `http://localhost:4317`)

## API

All routes under `/orders` require JWT authentication. The buyer ID, email, and name are extracted from the JWT `sub`, `email`, and `name`/`preferred_username` claims respectively.

### Endpoints

- `GET /orders`: fetch current user's orders with pagination and optional status filter
  - Query params: `offset` (default 0), `limit` (default 10), `status` (optional OrderStatus enum)
  - Returns paginated list of order summaries
- `GET /orders/{id}`: fetch specific order details
  - Returns `404` if order not found
  - Returns `403` if order belongs to different user
- `GET /orders/card-types`: list supported card types
  - Returns array of card brand objects with `name` and `displayName`
- `POST /orders`: create new order
  - Requires `X-Request-Id` header for idempotency
  - Clears user's basket after successful creation
  - Body: `CreateOrderRequest` with delivery address and payment details
- `PUT /orders/{id}/cancel`: cancel an order (within grace period)
  - Requires `X-Request-Id` header for idempotency
  - Body: optional `CancelOrderRequest` with cancellation reason
- `GET /healthz`: liveness probe (always returns `200`)
- `GET /healthz/ready`: readiness probe (checks database connectivity)

### Request/Response Formats

#### CreateOrderRequest
```json
{
  "street": "123 Main St",
  "city": "New York",
  "state": "NY",
  "country": "USA",
  "zipCode": "10001",
  "currency": "USD",
  "paymentDetails": {
    "cardType": "VISA",
    "cardNumber": "4111111111111111",
    "cardHolderName": "John Doe",
    "cardSecurityNumber": "123",
    "expirationMonth": 12,
    "expirationYear": 2025
  }
}
```

#### Order Response
```json
{
  "id": 123,
  "requestId": "uuid-string",
  "buyerId": "user-uuid",
  "buyerEmail": "user@example.com",
  "buyerName": "John Doe",
  "status": "Submitted",
  "deliveryAddress": {
    "street": "123 Main St",
    "city": "New York",
    "state": "NY",
    "country": "USA",
    "zipCode": "10001"
  },
  "items": [
    {
      "id": "item-uuid",
      "menuItemId": 1,
      "name": "Margherita Pizza",
      "pictureUrl": "https://...",
      "unitPrice": "12.99",
      "quantity": 2
    }
  ],
  "paymentMethod": {
    "cardType": "VISA",
    "cardLast4": "1111"
  },
  "totalPrice": "25.98",
  "currency": "USD",
  "description": null,
  "history": [
    {
      "id": 1,
      "orderId": 123,
      "status": "Submitted",
      "description": null,
      "createdAt": "2024-01-19T10:00:00Z"
    }
  ],
  "createdAt": "2024-01-19T10:00:00Z",
  "updatedAt": "2024-01-19T10:00:00Z"
}
```

#### Order Summary (for list endpoint)
```json
{
  "orders": [
    {
      "id": 123,
      "status": "Completed",
      "totalPrice": "25.98",
      "itemCount": 2,
      "description": null,
      "createdAt": "2024-01-19T10:00:00Z"
    }
  ],
  "total": 1,
  "offset": 0,
  "limit": 10
}
```

### Error Responses

- `400 Bad Request`: validation errors, missing required headers
  ```json
  {"message": "Validation failed", "reasons": ["quantity must be at least 1"]}
  ```
- `401 Unauthorized`: missing or invalid JWT
- `403 Forbidden`: attempting to access another user's order
- `404 Not Found`: order not found
  ```json
  {"message": "Order not found"}
  ```
- `409 Conflict`: duplicate request ID (idempotency violation)

## Event Integration

The service publishes and consumes events via RabbitMQ:

### Published Events
- `OrderCreatedEvent`: when order is successfully created
- `OrderCancelledEvent`: when order is cancelled
- `OrderStatusChangedEvent`: when order status changes

### Consumed Events
- `StockConfirmedEvent`: confirms stock availability, triggers payment processing
- `StockRejectedEvent`: stock not available, rejects order
- `PaymentSucceededEvent`: payment successful, completes order
- `PaymentFailedEvent`: payment failed, rejects order

- Exchange: `foodies`
- Consumes from queues: `order.stock-confirmed`, `order.stock-rejected`, `order.payment-succeeded`, `order.payment-failed`

## Order Status Flow

1. `Submitted` → Initial state after order creation
2. `PendingPayment` → Stock confirmed, awaiting payment
3. `Completed` → Payment successful, order fulfilled
4. `Rejected` → Stock unavailable or payment failed
5. `Cancelled` → User cancelled within grace period

## Running locally

```bash
# Start PostgreSQL (required)
docker run -d -p 5433:5432 -e POSTGRES_DB=foodies-order-database -e POSTGRES_USER=foodies_admin -e POSTGRES_PASSWORD=foodies_password postgres:16-alpine

# Start RabbitMQ (required)
docker run -d -p 5672:5672 -p 15672:15672 rabbitmq:3-management

# Start Basket service (required for order creation)
./gradlew :basket:run

# Start Payment service (required for payment processing)
./gradlew :payment:run

# Start Keycloak (required for authentication)
cd webapp && docker compose up keycloak -d

# Run order service
./gradlew :order:run
```

## Example curl commands

```bash
# Get user orders (requires valid JWT token)
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8084/orders?offset=0&limit=10"

# Get specific order
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8084/orders/123

# Get supported card types
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8084/orders/card-types

# Create order
curl -X POST http://localhost:8084/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Request-Id: $(uuidgen)" \
  -d '{
    "street": "123 Main St",
    "city": "New York", 
    "state": "NY",
    "country": "USA",
    "zipCode": "10001",
    "currency": "USD",
    "paymentDetails": {
      "cardType": "VISA",
      "cardNumber": "4111111111111111",
      "cardHolderName": "John Doe",
      "cardSecurityNumber": "123",
      "expirationMonth": 12,
      "expirationYear": 2025
    }
  }'

# Cancel order
curl -X PUT http://localhost:8084/orders/123/cancel \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Request-Id: $(uuidgen)" \
  -d '{"reason": "Changed mind"}'

# Health checks
curl http://localhost:8084/healthz
curl http://localhost:8084/healthz/ready
```

## Grace Period Cancellation

Orders can be cancelled within the configured grace period (default 60 seconds) after creation. Cancellation is only allowed if the order is still in `Submitted` status and hasn't proceeded to payment processing.

## Database Schema

- `orders`: Main order table
- `order_items`: Order line items
- `order_history`: Status change history
- `payment_methods`: Payment information

Migrations located in `src/main/resources/db/migration/`.

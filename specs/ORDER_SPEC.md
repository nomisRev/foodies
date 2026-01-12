# Order Service Specification

## Overview

The Order Service is a microservice responsible for managing the complete order lifecycle in the Foodies application. It handles order creation from baskets, order state transitions, payment coordination, and provides a complete audit trail of order history.

This specification is inspired by the [eShop Ordering Service](https://github.com/dotnet/eShop) architecture, adapted for the Foodies Kotlin/Ktor technology stack with event-driven patterns.

## Architecture

### Service Details

| Property | Value |
|----------|-------|
| Port | 8084 |
| Module | `order` |
| Package | `io.ktor.foodies.order` |
| Data Store | PostgreSQL |
| Protocol | REST API (HTTP/JSON) |
| Messaging | RabbitMQ (Events) |

### High-Level Architecture

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   WebApp    │────▶│    Order    │────▶│ PostgreSQL  │
│  (Port 8080)│     │ (Port 8084) │     │             │
└─────────────┘     └─────────────┘     └─────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
 ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
 │   Basket    │    │    Menu     │    │  RabbitMQ   │
 │ (Port 8083) │    │ (Port 8082) │    │  (Events)   │
 └─────────────┘    └─────────────┘    └─────────────┘
```

### Event Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           ORDER LIFECYCLE                                    │
└─────────────────────────────────────────────────────────────────────────────┘

User Checkout                                           External Services
    │                                                          │
    ▼                                                          │
┌─────────┐    OrderCreatedEvent    ┌─────────┐               │
│Submitted│──────────────────────▶ │ Basket  │ (clears basket)│
└────┬────┘                        └─────────┘               │
     │                                                        │
     │ Grace Period (configurable delay)                      │
     ▼                                                        │
┌─────────────────────┐   OrderAwaitingValidationEvent        │
│AwaitingValidation   │──────────────────────────────▶ ┌──────┴──────┐
└──────────┬──────────┘                                │    Menu     │
           │                                           │  (validates │
           │◀──────────────────────────────────────────│   stock)    │
           │    StockConfirmedEvent / StockRejectedEvent└─────────────┘
           │
     ┌─────┴─────┐
     │           │
     ▼           ▼
┌─────────┐  ┌─────────┐
│Confirmed│  │Cancelled│ (stock rejected)
└────┬────┘  └─────────┘
     │
     │ PaymentSucceededEvent / PaymentFailedEvent
     │
     ├─────────────────────────┐
     ▼                         ▼
┌─────────┐              ┌─────────┐
│  Paid   │              │Cancelled│ (payment failed)
└────┬────┘              └─────────┘
     │
     │ Admin ships order
     ▼
┌─────────┐
│ Shipped │
└─────────┘
```

## Domain Model

### Order Aggregate Root

The Order is the root aggregate containing all order-related data and enforcing business rules.

```kotlin
@Serializable
data class Order(
    val id: Long,
    val buyerId: String,                        // Keycloak user ID
    val buyerEmail: String,                     // User email for notifications
    val buyerName: String,                      // Display name
    val status: OrderStatus,
    val deliveryAddress: Address,
    val items: List<OrderItem>,
    val paymentMethod: PaymentMethod?,          // Set after payment verification
    val totalPrice: SerializableBigDecimal,
    val description: String?,                   // Status description (e.g., rejection reason)
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

### OrderStatus

```kotlin
@Serializable
enum class OrderStatus {
    Submitted,              // Initial state after order creation
    AwaitingValidation,     // Grace period ended, awaiting stock validation
    StockConfirmed,         // Menu service confirmed item availability
    Paid,                   // Payment confirmed
    Shipped,                // Order shipped to customer
    Cancelled               // Order cancelled (by user, stock rejection, or payment failure)
}
```

### OrderItem

```kotlin
@Serializable
data class OrderItem(
    val id: Long,
    val menuItemId: Long,                       // Reference to Menu service
    val menuItemName: String,                   // Denormalized at order time
    val pictureUrl: String,                     // Denormalized at order time
    val unitPrice: SerializableBigDecimal,      // Price at order time (locked in)
    val quantity: Int,                          // Must be >= 1
    val discount: SerializableBigDecimal = SerializableBigDecimal.ZERO,
)
```

### Address (Value Object)

```kotlin
@Serializable
data class Address(
    val street: String,
    val city: String,
    val state: String,
    val country: String,
    val zipCode: String,
)
```

### PaymentMethod

```kotlin
@Serializable
data class PaymentMethod(
    val id: Long,
    val cardType: CardType,
    val cardHolderName: String,
    val cardNumber: String,                     // Last 4 digits only (masked)
    val expirationMonth: Int,
    val expirationYear: Int,
)

@Serializable
enum class CardType {
    Visa,
    MasterCard,
    Amex,
}
```

### Design Decisions

1. **Denormalization**: Menu item data (name, price, image) is stored in OrderItem to:
   - Lock in the price at order time
   - Preserve historical data even if menu items change
   - Enable display without Menu service dependency

2. **Event-Driven State Machine**: Order status changes are driven by events, enabling:
   - Loose coupling between services
   - Clear audit trail
   - Retry and compensation patterns

3. **Idempotent Operations**: All state transitions use idempotent patterns to handle retries safely

4. **Grace Period**: A configurable delay between Submitted and AwaitingValidation allows users to cancel immediately after ordering

## Database Schema

### Orders Table

```sql
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    buyer_id VARCHAR(255) NOT NULL,
    buyer_email VARCHAR(255) NOT NULL,
    buyer_name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'Submitted',

    -- Delivery Address (embedded)
    street VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    country VARCHAR(100) NOT NULL,
    zip_code VARCHAR(20) NOT NULL,

    -- Payment Method (nullable until payment)
    payment_method_id BIGINT REFERENCES payment_methods(id),

    total_price NUMERIC(10, 2) NOT NULL CHECK (total_price > 0),
    description TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Indexes
    CONSTRAINT orders_buyer_id_idx UNIQUE (buyer_id, id)
);

CREATE INDEX orders_status_idx ON orders(status);
CREATE INDEX orders_created_at_idx ON orders(created_at DESC);
```

### Order Items Table

```sql
CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    menu_item_id BIGINT NOT NULL,
    menu_item_name VARCHAR(255) NOT NULL,
    picture_url TEXT NOT NULL,
    unit_price NUMERIC(10, 2) NOT NULL CHECK (unit_price > 0),
    quantity INT NOT NULL CHECK (quantity > 0),
    discount NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (discount >= 0),

    -- Ensure discount doesn't exceed total
    CONSTRAINT discount_not_exceeds_total CHECK (discount <= unit_price * quantity)
);

CREATE INDEX order_items_order_id_idx ON order_items(order_id);
```

### Payment Methods Table

```sql
CREATE TABLE payment_methods (
    id BIGSERIAL PRIMARY KEY,
    buyer_id VARCHAR(255) NOT NULL,
    card_type VARCHAR(20) NOT NULL,
    card_holder_name VARCHAR(255) NOT NULL,
    card_number_masked VARCHAR(4) NOT NULL,     -- Last 4 digits only
    expiration_month INT NOT NULL CHECK (expiration_month BETWEEN 1 AND 12),
    expiration_year INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Unique constraint per buyer + card
    CONSTRAINT payment_methods_unique UNIQUE (buyer_id, card_type, card_number_masked)
);

CREATE INDEX payment_methods_buyer_id_idx ON payment_methods(buyer_id);
```

### Idempotency Table

```sql
CREATE TABLE processed_requests (
    request_id UUID PRIMARY KEY,
    command_type VARCHAR(100) NOT NULL,
    result JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Cleanup old entries (keep for 24 hours)
CREATE INDEX processed_requests_created_at_idx ON processed_requests(created_at);
```

## API Specification

### Base Path

```
/orders
```

### Authentication

All endpoints require authentication via Keycloak JWT. The `buyerId` is extracted from the JWT `sub` (subject) claim.

### Endpoints

#### POST /orders

Creates a new order from the user's basket.

**Request**
- Headers:
  - `Authorization: Bearer <token>`
  - `X-Request-Id: <uuid>` (for idempotency)
- Body:
```json
{
  "deliveryAddress": {
    "street": "123 Food Street",
    "city": "San Francisco",
    "state": "CA",
    "country": "USA",
    "zipCode": "94102"
  },
  "paymentDetails": {
    "cardType": "Visa",
    "cardNumber": "4111111111111111",
    "cardHolderName": "John Doe",
    "cardSecurityNumber": "123",
    "expirationMonth": 12,
    "expirationYear": 2025
  }
}
```

**Response**
- `201 Created`: Order created
- `400 Bad Request`: Invalid input or empty basket
- `401 Unauthorized`: Missing or invalid token
- `409 Conflict`: Duplicate request (idempotency)

```json
{
  "id": 12345,
  "buyerId": "user-uuid-123",
  "status": "Submitted",
  "items": [...],
  "totalPrice": "45.97",
  "createdAt": "2025-01-11T10:00:00Z"
}
```

**Behavior**
1. Validate X-Request-Id for idempotency
2. Fetch basket from Basket service
3. Validate basket is not empty
4. Create Order with Submitted status
5. Create OrderItems from basket items
6. Store payment method (masked)
7. Publish `OrderCreatedEvent` -> Basket clears
8. Return created order

#### GET /orders

Lists the current user's orders with pagination.

**Request**
- Headers: `Authorization: Bearer <token>`
- Query Parameters:
  - `offset` (default: 0)
  - `limit` (default: 10, max: 50)
  - `status` (optional): Filter by status

**Response**
- `200 OK`: Returns paginated list
- `401 Unauthorized`: Missing or invalid token

```json
{
  "orders": [
    {
      "id": 12345,
      "status": "Shipped",
      "totalPrice": "45.97",
      "itemCount": 3,
      "createdAt": "2025-01-11T10:00:00Z"
    }
  ],
  "total": 15,
  "offset": 0,
  "limit": 10
}
```

#### GET /orders/{orderId}

Gets detailed information about a specific order.

**Request**
- Headers: `Authorization: Bearer <token>`
- Path: `orderId` - the order ID

**Response**
- `200 OK`: Returns order details
- `401 Unauthorized`: Missing or invalid token
- `403 Forbidden`: Order belongs to different user
- `404 Not Found`: Order not found

```json
{
  "id": 12345,
  "buyerId": "user-uuid-123",
  "buyerEmail": "john@example.com",
  "buyerName": "John Doe",
  "status": "Paid",
  "deliveryAddress": {
    "street": "123 Food Street",
    "city": "San Francisco",
    "state": "CA",
    "country": "USA",
    "zipCode": "94102"
  },
  "items": [
    {
      "id": 1,
      "menuItemId": 42,
      "menuItemName": "Margherita Pizza",
      "pictureUrl": "https://...",
      "unitPrice": "12.99",
      "quantity": 2,
      "discount": "0.00"
    }
  ],
  "paymentMethod": {
    "cardType": "Visa",
    "cardHolderName": "John Doe",
    "cardNumber": "1111",
    "expirationMonth": 12,
    "expirationYear": 2025
  },
  "totalPrice": "45.97",
  "description": null,
  "createdAt": "2025-01-11T10:00:00Z",
  "updatedAt": "2025-01-11T10:05:00Z"
}
```

#### PUT /orders/{orderId}/cancel

Cancels an order. Only allowed before payment (Submitted, AwaitingValidation, StockConfirmed statuses).

**Request**
- Headers:
  - `Authorization: Bearer <token>`
  - `X-Request-Id: <uuid>` (for idempotency)
- Path: `orderId` - the order ID

**Response**
- `200 OK`: Order cancelled
- `400 Bad Request`: Order cannot be cancelled (already paid/shipped)
- `401 Unauthorized`: Missing or invalid token
- `403 Forbidden`: Order belongs to different user
- `404 Not Found`: Order not found

```json
{
  "id": 12345,
  "status": "Cancelled",
  "description": "Cancelled by user"
}
```

**Business Rules**
- Cannot cancel if status is `Paid` or `Shipped`
- Publishes `OrderCancelledEvent`

#### GET /orders/card-types

Returns the list of supported card types.

**Request**
- Headers: `Authorization: Bearer <token>`

**Response**
- `200 OK`: Returns card types

```json
[
  { "id": "Visa", "name": "Visa" },
  { "id": "MasterCard", "name": "MasterCard" },
  { "id": "Amex", "name": "American Express" }
]
```

### Admin Endpoints

These endpoints are restricted to admin users (Keycloak role: `admin`).

#### PUT /admin/orders/{orderId}/ship

Marks an order as shipped. Only allowed for `Paid` orders.

**Request**
- Headers:
  - `Authorization: Bearer <token>` (admin role required)
  - `X-Request-Id: <uuid>` (for idempotency)
- Path: `orderId` - the order ID

**Response**
- `200 OK`: Order shipped
- `400 Bad Request`: Order cannot be shipped (not paid)
- `401 Unauthorized`: Missing or invalid token
- `403 Forbidden`: Not an admin
- `404 Not Found`: Order not found

```json
{
  "id": 12345,
  "status": "Shipped"
}
```

#### GET /admin/orders

Lists all orders (admin view with filtering).

**Request**
- Headers: `Authorization: Bearer <token>` (admin role required)
- Query Parameters:
  - `offset` (default: 0)
  - `limit` (default: 20, max: 100)
  - `status` (optional): Filter by status
  - `buyerId` (optional): Filter by buyer

**Response**
- `200 OK`: Returns paginated list with buyer information

## Event Integration

### Published Events

#### OrderCreatedEvent

Published when an order is successfully created.

```kotlin
@Serializable
data class OrderCreatedEvent(
    val orderId: Long,
    val buyerId: String,
    val items: List<OrderItemSnapshot>,
    val totalPrice: SerializableBigDecimal,
    val createdAt: Instant,
)

@Serializable
data class OrderItemSnapshot(
    val menuItemId: Long,
    val quantity: Int,
    val unitPrice: SerializableBigDecimal,
)
```

**RabbitMQ Configuration**
- Exchange: `foodies.events`
- Routing Key: `order.created`

**Subscribers**
- Basket Service: Clears the user's basket

#### OrderAwaitingValidationEvent

Published when grace period ends and stock validation is needed.

```kotlin
@Serializable
data class OrderAwaitingValidationEvent(
    val orderId: Long,
    val buyerId: String,
    val items: List<StockValidationItem>,
)

@Serializable
data class StockValidationItem(
    val menuItemId: Long,
    val quantity: Int,
)
```

**RabbitMQ Configuration**
- Exchange: `foodies.events`
- Routing Key: `order.awaiting-validation`

**Subscribers**
- Menu Service: Validates stock availability

#### OrderStatusChangedEvent

Published on any status change for UI notifications.

```kotlin
@Serializable
data class OrderStatusChangedEvent(
    val orderId: Long,
    val buyerId: String,
    val oldStatus: OrderStatus,
    val newStatus: OrderStatus,
    val description: String?,
    val changedAt: Instant,
)
```

**RabbitMQ Configuration**
- Exchange: `foodies.events`
- Routing Key: `order.status-changed`

**Subscribers**
- WebApp: Updates UI via SSE/WebSocket

#### OrderCancelledEvent

Published when an order is cancelled.

```kotlin
@Serializable
data class OrderCancelledEvent(
    val orderId: Long,
    val buyerId: String,
    val reason: String,
    val cancelledAt: Instant,
)
```

**RabbitMQ Configuration**
- Exchange: `foodies.events`
- Routing Key: `order.cancelled`

### Consumed Events

#### StockConfirmedEvent

From Menu Service when all items are available.

```kotlin
@Serializable
data class StockConfirmedEvent(
    val orderId: Long,
    val confirmedAt: Instant,
)
```

**Handler**
```kotlin
class StockConfirmedEventHandler(
    private val orderService: OrderService
) {
    suspend fun handle(event: StockConfirmedEvent) {
        orderService.setStockConfirmed(event.orderId)
        // Publishes OrderStatusChangedEvent
    }
}
```

#### StockRejectedEvent

From Menu Service when items are unavailable.

```kotlin
@Serializable
data class StockRejectedEvent(
    val orderId: Long,
    val rejectedItems: List<RejectedItem>,
    val rejectedAt: Instant,
)

@Serializable
data class RejectedItem(
    val menuItemId: Long,
    val menuItemName: String,
    val requestedQuantity: Int,
    val availableQuantity: Int,
)
```

**Handler**
```kotlin
class StockRejectedEventHandler(
    private val orderService: OrderService
) {
    suspend fun handle(event: StockRejectedEvent) {
        val reason = buildRejectionReason(event.rejectedItems)
        orderService.cancelOrderDueToStockRejection(event.orderId, reason)
        // Publishes OrderCancelledEvent
    }
}
```

#### PaymentSucceededEvent

From Payment Service when payment is confirmed.

```kotlin
@Serializable
data class PaymentSucceededEvent(
    val orderId: Long,
    val paymentId: String,
    val amount: SerializableBigDecimal,
    val processedAt: Instant,
)
```

**Handler**
```kotlin
class PaymentSucceededEventHandler(
    private val orderService: OrderService
) {
    suspend fun handle(event: PaymentSucceededEvent) {
        orderService.setPaid(event.orderId)
        // Publishes OrderStatusChangedEvent
    }
}
```

#### PaymentFailedEvent

From Payment Service when payment fails.

```kotlin
@Serializable
data class PaymentFailedEvent(
    val orderId: Long,
    val reason: String,
    val failedAt: Instant,
)
```

**Handler**
```kotlin
class PaymentFailedEventHandler(
    private val orderService: OrderService
) {
    suspend fun handle(event: PaymentFailedEvent) {
        orderService.cancelOrderDueToPaymentFailure(event.orderId, event.reason)
        // Publishes OrderCancelledEvent
    }
}
```

### RabbitMQ Queue Bindings

| Queue | Exchange | Routing Key | Purpose |
|-------|----------|-------------|---------|
| `order.stock-confirmed` | `foodies.events` | `stock.confirmed` | Stock validation success |
| `order.stock-rejected` | `foodies.events` | `stock.rejected` | Stock validation failure |
| `order.payment-succeeded` | `foodies.events` | `payment.succeeded` | Payment success |
| `order.payment-failed` | `foodies.events` | `payment.failed` | Payment failure |

## Service Dependencies

### Basket Service Integration

The Order service calls the Basket service to retrieve items during checkout.

```kotlin
interface BasketClient {
    suspend fun getBasket(buyerId: String, token: String): CustomerBasket?
}

class HttpBasketClient(
    private val httpClient: HttpClient,
    private val config: BasketClientConfig
) : BasketClient {

    override suspend fun getBasket(buyerId: String, token: String): CustomerBasket? {
        return try {
            httpClient.get("${config.baseUrl}/basket") {
                bearerAuth(token)
            }.body<CustomerBasket>()
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) null
            else throw e
        }
    }
}
```

### Configuration

```yaml
basket:
  baseUrl: ${BASKET_SERVICE_URL:http://localhost:8083}
```

## Order State Machine

### State Transitions

```
                                    ┌──────────────────┐
                                    │                  │
                                    ▼                  │
┌───────────┐  grace period   ┌───────────────────┐    │
│ Submitted │────────────────▶│AwaitingValidation │    │
└───────────┘                 └─────────┬─────────┘    │
      │                                 │              │
      │ user cancels                    │              │
      │                     ┌───────────┴───────────┐  │
      │                     │                       │  │
      │                     ▼                       ▼  │
      │             ┌───────────────┐        ┌─────────┴─┐
      └────────────▶│   Cancelled   │◀───────│StockConfirmed│
                    └───────────────┘        └─────┬─────┘
                            ▲                      │
                            │                      │ payment
                            │                      │
                            │              ┌───────┴───────┐
                            │              │               │
                            │              ▼               ▼
                            │        ┌──────────┐   ┌───────────┐
                            └────────│   Paid   │   │ Cancelled │
                                     └────┬─────┘   │(payment   │
                                          │         │ failed)   │
                                          │ ship    └───────────┘
                                          ▼
                                     ┌──────────┐
                                     │ Shipped  │
                                     └──────────┘
```

### Transition Rules

| From | To | Trigger | Conditions |
|------|----|---------|------------|
| - | Submitted | User checkout | Valid basket, valid payment |
| Submitted | AwaitingValidation | Grace period timer | Automatic |
| Submitted | Cancelled | User request | - |
| AwaitingValidation | StockConfirmed | StockConfirmedEvent | - |
| AwaitingValidation | Cancelled | StockRejectedEvent or user request | - |
| StockConfirmed | Paid | PaymentSucceededEvent | - |
| StockConfirmed | Cancelled | PaymentFailedEvent or user request | - |
| Paid | Shipped | Admin action | - |

### Invalid Transitions

- Cannot cancel `Paid` or `Shipped` orders
- Cannot ship orders that aren't `Paid`
- Cannot modify order items after `Submitted`

## Business Rules & Validation

### Order Creation

```kotlin
fun validateCreateOrder(request: CreateOrderRequest): ValidationResult {
    validate {
        // Address validation
        request.deliveryAddress.street.validate(String::isNotBlank) { "Street is required" }
        request.deliveryAddress.city.validate(String::isNotBlank) { "City is required" }
        request.deliveryAddress.state.validate(String::isNotBlank) { "State is required" }
        request.deliveryAddress.country.validate(String::isNotBlank) { "Country is required" }
        request.deliveryAddress.zipCode.validate(String::isNotBlank) { "Zip code is required" }

        // Payment validation
        request.paymentDetails.cardNumber.validate({ it.length in 13..19 }) {
            "Card number must be 13-19 digits"
        }
        request.paymentDetails.cardHolderName.validate(String::isNotBlank) {
            "Card holder name is required"
        }
        request.paymentDetails.cardSecurityNumber.validate({ it.length in 3..4 }) {
            "CVV must be 3-4 digits"
        }
        request.paymentDetails.validate({ it.isNotExpired() }) {
            "Card is expired"
        }
    }
}
```

### Order Item Validation

```kotlin
fun validateOrderItem(item: BasketItem): ValidationResult {
    validate {
        item.quantity.validate({ it > 0 }) { "Quantity must be positive" }
        item.unitPrice.validate({ it > BigDecimal.ZERO }) { "Price must be positive" }
    }
}
```

## Project Structure

```
order/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── io/ktor/foodies/order/
│   │   │       ├── App.kt                    # Application entry point
│   │   │       ├── Config.kt                 # Configuration classes
│   │   │       ├── Module.kt                 # Dependency injection
│   │   │       │
│   │   │       ├── domain/
│   │   │       │   ├── Order.kt              # Order aggregate
│   │   │       │   ├── OrderItem.kt          # Order item entity
│   │   │       │   ├── OrderStatus.kt        # Status enum
│   │   │       │   ├── Address.kt            # Value object
│   │   │       │   ├── PaymentMethod.kt      # Payment entity
│   │   │       │   └── CardType.kt           # Card type enum
│   │   │       │
│   │   │       ├── api/
│   │   │       │   ├── Routes.kt             # HTTP route definitions
│   │   │       │   ├── AdminRoutes.kt        # Admin endpoints
│   │   │       │   ├── Requests.kt           # Request DTOs
│   │   │       │   └── Responses.kt          # Response DTOs
│   │   │       │
│   │   │       ├── service/
│   │   │       │   ├── OrderService.kt       # Business logic
│   │   │       │   ├── IdempotencyService.kt # Request deduplication
│   │   │       │   └── GracePeriodService.kt # Grace period handling
│   │   │       │
│   │   │       ├── repository/
│   │   │       │   ├── OrderRepository.kt    # Order data access
│   │   │       │   ├── PaymentMethodRepository.kt
│   │   │       │   └── IdempotencyRepository.kt
│   │   │       │
│   │   │       ├── clients/
│   │   │       │   └── BasketClient.kt       # Basket service client
│   │   │       │
│   │   │       └── events/
│   │   │           ├── published/
│   │   │           │   ├── OrderCreatedEvent.kt
│   │   │           │   ├── OrderAwaitingValidationEvent.kt
│   │   │           │   ├── OrderStatusChangedEvent.kt
│   │   │           │   └── OrderCancelledEvent.kt
│   │   │           ├── consumed/
│   │   │           │   ├── StockConfirmedEvent.kt
│   │   │           │   ├── StockRejectedEvent.kt
│   │   │           │   ├── PaymentSucceededEvent.kt
│   │   │           │   └── PaymentFailedEvent.kt
│   │   │           ├── handlers/
│   │   │           │   ├── StockConfirmedEventHandler.kt
│   │   │           │   ├── StockRejectedEventHandler.kt
│   │   │           │   ├── PaymentSucceededEventHandler.kt
│   │   │           │   └── PaymentFailedEventHandler.kt
│   │   │           └── EventPublisher.kt
│   │   │
│   │   └── resources/
│   │       ├── application.yaml
│   │       └── db/migration/
│   │           ├── V1__create_orders_table.sql
│   │           ├── V2__create_order_items_table.sql
│   │           ├── V3__create_payment_methods_table.sql
│   │           └── V4__create_idempotency_table.sql
│   │
│   └── test/
│       └── kotlin/
│           └── io/ktor/foodies/order/
│               ├── OrderServiceSpec.kt
│               ├── OrderRepositorySpec.kt
│               ├── OrderRoutesSpec.kt
│               └── OrderStateMachineSpec.kt
│
├── build.gradle.kts
└── README.md
```

## Configuration

### application.yaml

```yaml
config:
  host: "$HOST:0.0.0.0"
  port: "$PORT:8084"

  auth:
    issuer: "$AUTH_ISSUER:http://localhost:9090/realms/foodies-keycloak"
    audience: "$AUTH_AUDIENCE:account"

  data_source:
    url: "$DB_URL:jdbc:postgresql://localhost:5434/foodies-order-database"
    username: "$DB_USERNAME:foodies_admin"
    password: "$DB_PASSWORD:foodies_password"

  rabbit:
    host: "$RABBITMQ_HOST:localhost"
    port: "$RABBITMQ_PORT:5672"
    username: "$RABBITMQ_USERNAME:guest"
    password: "$RABBITMQ_PASSWORD:guest"

  basket:
    base_url: "$BASKET_SERVICE_URL:http://localhost:8083"

  order:
    grace_period_seconds: "$GRACE_PERIOD_SECONDS:120"
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| HOST | Server bind host | 0.0.0.0 |
| PORT | Server port | 8084 |
| AUTH_ISSUER | Keycloak issuer URL | http://localhost:9090/realms/foodies-keycloak |
| AUTH_AUDIENCE | JWT audience | account |
| DB_URL | PostgreSQL connection string | jdbc:postgresql://localhost:5434/foodies-order-database |
| DB_USERNAME | Database username | foodies_admin |
| DB_PASSWORD | Database password | foodies_password |
| RABBITMQ_HOST | RabbitMQ host | localhost |
| RABBITMQ_PORT | RabbitMQ port | 5672 |
| RABBITMQ_USERNAME | RabbitMQ username | guest |
| RABBITMQ_PASSWORD | RabbitMQ password | guest |
| BASKET_SERVICE_URL | Basket service base URL | http://localhost:8083 |
| GRACE_PERIOD_SECONDS | Grace period before stock validation | 120 |

## Idempotency Pattern

All mutating operations support idempotency via the `X-Request-Id` header.

```kotlin
class IdempotencyService(
    private val repository: IdempotencyRepository
) {
    suspend fun <T> executeIdempotent(
        requestId: UUID,
        commandType: String,
        operation: suspend () -> T
    ): T {
        // Check if already processed
        val existing = repository.findByRequestId(requestId)
        if (existing != null) {
            return Json.decodeFromString(existing.result)
        }

        // Execute operation
        val result = operation()

        // Store result for future duplicate requests
        repository.save(ProcessedRequest(
            requestId = requestId,
            commandType = commandType,
            result = Json.encodeToString(result)
        ))

        return result
    }
}
```

## Grace Period Implementation

The grace period allows users to cancel orders immediately after creation.

```kotlin
class GracePeriodService(
    private val config: OrderConfig,
    private val orderService: OrderService,
    private val scope: CoroutineScope
) {
    fun scheduleGracePeriodExpiration(orderId: Long) {
        scope.launch {
            delay(config.gracePeriodSeconds.seconds)
            orderService.transitionToAwaitingValidation(orderId)
        }
    }
}
```

**Alternative: Using a scheduled job**

For production deployments, consider using a database-backed scheduler or message queue with delayed delivery to handle grace period expiration reliably across service restarts.

## Kubernetes Deployment

### Order Database Deployment

```yaml
# k8s/databases/order-database.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-database
  namespace: foodies
spec:
  replicas: 1
  selector:
    matchLabels:
      app: order-database
  template:
    metadata:
      labels:
        app: order-database
    spec:
      containers:
      - name: postgres
        image: postgres:16-alpine
        ports:
        - containerPort: 5432
        env:
        - name: POSTGRES_DB
          value: "foodies-order-database"
        - name: POSTGRES_USER
          valueFrom:
            secretKeyRef:
              name: order-database-secret
              key: username
        - name: POSTGRES_PASSWORD
          valueFrom:
            secretKeyRef:
              name: order-database-secret
              key: password
        volumeMounts:
        - name: order-data
          mountPath: /var/lib/postgresql/data
      volumes:
      - name: order-data
        persistentVolumeClaim:
          claimName: order-database-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: order-database
  namespace: foodies
spec:
  selector:
    app: order-database
  ports:
  - port: 5432
    targetPort: 5432
```

### Order Service Deployment

```yaml
# k8s/services/order.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order
  namespace: foodies
spec:
  replicas: 2
  selector:
    matchLabels:
      app: order
  template:
    metadata:
      labels:
        app: order
    spec:
      containers:
      - name: order
        image: foodies-order:latest
        ports:
        - containerPort: 8084
        env:
        - name: PORT
          value: "8084"
        - name: AUTH_ISSUER
          valueFrom:
            configMapKeyRef:
              name: foodies-config
              key: AUTH_ISSUER
        - name: DB_URL
          value: "jdbc:postgresql://order-database:5432/foodies-order-database"
        - name: DB_USERNAME
          valueFrom:
            secretKeyRef:
              name: order-database-secret
              key: username
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: order-database-secret
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
        - name: BASKET_SERVICE_URL
          value: "http://basket:8083"
        resources:
          requests:
            memory: "256Mi"
            cpu: "100m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        readinessProbe:
          httpGet:
            path: /healthz
            port: 8084
          initialDelaySeconds: 10
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: /healthz
            port: 8084
          initialDelaySeconds: 30
          periodSeconds: 20
---
apiVersion: v1
kind: Service
metadata:
  name: order
  namespace: foodies
spec:
  selector:
    app: order
  ports:
  - port: 8084
    targetPort: 8084
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
    mainClass.set("io.ktor.foodies.order.AppKt")
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-server-auth-jwt")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-call-id")

    // Ktor Client (for Basket service)
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-content-negotiation")

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

1. **OrderServiceSpec**: Test business logic
   - Creating orders from baskets
   - State transitions
   - Validation errors
   - Idempotency handling

2. **OrderStateMachineSpec**: Test state transitions
   - Valid transitions
   - Invalid transition rejections
   - Event publishing on transitions

### Integration Tests

1. **OrderRepositorySpec**: Test database operations
   - CRUD operations
   - Pagination
   - Filtering
   - Transaction handling

2. **OrderRoutesSpec**: Test HTTP endpoints
   - Authentication/authorization
   - Request/response serialization
   - Error handling
   - Idempotency headers

3. **EventHandlerSpec**: Test event processing
   - Stock confirmed/rejected handling
   - Payment success/failure handling

### Example Test

```kotlin
class OrderServiceSpec : TestBalloon() {

    val spec by testSuite {
        val testModule = testOrderModule()

        test("creating order from basket succeeds") {
            val module = testModule()
            val basket = CustomerBasket(
                buyerId = "user-123",
                items = listOf(
                    BasketItem(
                        id = "item-1",
                        menuItemId = 42,
                        menuItemName = "Pizza",
                        menuItemDescription = "Delicious",
                        menuItemImageUrl = "http://...",
                        unitPrice = BigDecimal("12.99"),
                        quantity = 2
                    )
                )
            )

            module.basketClient.setBasket(basket)

            val order = module.orderService.createOrder(
                buyerId = "user-123",
                request = CreateOrderRequest(
                    deliveryAddress = testAddress(),
                    paymentDetails = testPaymentDetails()
                ),
                requestId = UUID.randomUUID()
            )

            assertEquals(OrderStatus.Submitted, order.status)
            assertEquals(1, order.items.size)
            assertEquals(BigDecimal("25.98"), order.totalPrice)
        }

        test("cannot cancel paid order") {
            val module = testModule()
            val order = module.createPaidOrder()

            val error = assertFailsWith<OrderException> {
                module.orderService.cancelOrder(order.id, "user-123")
            }

            assertEquals("Cannot cancel order in Paid status", error.message)
        }
    }
}
```

## Health Check

```kotlin
fun Route.healthRoutes(dataSource: HikariDataSource) {
    get("/healthz") {
        call.respond(HttpStatusCode.OK)
    }

    get("/healthz/ready") {
        val dbHealthy = runCatching {
            dataSource.connection.use { it.isValid(5) }
        }.getOrDefault(false)

        val status = if (dbHealthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        call.respond(status, mapOf("database" to if (dbHealthy) "UP" else "DOWN"))
    }
}
```

## Security Considerations

1. **Authentication**: All order operations require valid JWT from Keycloak
2. **Authorization**:
   - Users can only access their own orders
   - Admin role required for shipping and admin endpoints
3. **Payment Data**:
   - Only store last 4 digits of card number
   - Never log full card numbers
   - CVV never stored
4. **Input Validation**: Comprehensive validation of all inputs
5. **Rate Limiting**: Consider rate limiting order creation
6. **Idempotency**: All mutating operations are idempotent

## API Summary

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | /orders | Create order from basket | Required |
| GET | /orders | List user's orders | Required |
| GET | /orders/{orderId} | Get order details | Required |
| PUT | /orders/{orderId}/cancel | Cancel order | Required |
| GET | /orders/card-types | Get supported card types | Required |
| PUT | /admin/orders/{orderId}/ship | Ship order (admin) | Admin |
| GET | /admin/orders | List all orders (admin) | Admin |
| GET | /healthz | Health check | None |
| GET | /healthz/ready | Readiness check | None |

## Future Enhancements

1. **Order History**: Full audit trail of all order state changes
2. **Order Notifications**: Email/push notifications on status changes
3. **Partial Fulfillment**: Handle orders with mixed availability
4. **Refunds**: Support for cancelling shipped orders with refunds
5. **Order Tracking**: Integration with shipping providers
6. **Analytics**: Order metrics and reporting
7. **Promotions**: Discount codes and promotional pricing
8. **Split Payments**: Support for multiple payment methods
9. **Guest Checkout**: Orders without requiring account creation
10. **Order Modifications**: Allow item changes during grace period

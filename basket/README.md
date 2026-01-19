# Basket Service

Shopping basket service backed by Redis. Manages user shopping baskets with JWT authentication and integrates with the Menu service for item details.

> For common architectural patterns, testing approach, and build commands, see the [main README](../README.md#architecture-patterns).

## Configuration

Values come from `config` in `src/main/resources/application.yaml` (env overrides shown in parentheses):

- `HOST` / `PORT`: bind address and port (default `0.0.0.0:8083`)
- `AUTH_ISSUER`: Keycloak OIDC issuer URL (default `http://localhost:9090/realms/foodies-keycloak`)
- `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD`: Redis connection (defaults to `localhost:6379`, empty password)
- `MENU_SERVICE_URL`: Menu service base URL (default `http://localhost:8082`)
- `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD`: RabbitMQ connection (defaults to `localhost:5672`, `guest/guest`)
- `RABBITMQ_EXCHANGE`: RabbitMQ exchange name (default `foodies`)
- `RABBITMQ_QUEUE`: Event queue name (default `basket.order-created`)
- `OTEL_EXPORTER_OTLP_ENDPOINT`: OpenTelemetry endpoint (default `http://localhost:4317`)

## API

All routes under `/basket` require JWT authentication. The buyer ID is extracted from the JWT `sub` claim.

### Endpoints

- `GET /basket`: fetch current user's basket (empty basket if none exists)
- `POST /basket/items`: add item to basket
  - Body: `{"menuItemId": 1, "quantity": 2}`
  - Fetches item details from Menu service and denormalizes into basket
  - Increments quantity if item already exists
- `PUT /basket/items/{itemId}`: update item quantity
  - Body: `{"quantity": 3}`
  - Returns `404` if item not found
- `DELETE /basket/items/{itemId}`: remove item from basket
- `DELETE /basket`: clear entire basket, returns `204`
- `GET /healthz/startup`: startup probe
- `GET /healthz/liveness`: liveness probe
- `GET /healthz/readiness`: readiness probe (checks Redis, Menu service, and RabbitMQ connectivity)

### Response Format

```json
{
  "buyerId": "user-uuid-123",
  "items": [
    {
      "id": "item-uuid-456",
      "menuItemId": 1,
      "menuItemName": "Margherita Pizza",
      "menuItemDescription": "Classic tomato and mozzarella",
      "menuItemImageUrl": "https://...",
      "unitPrice": "12.99",
      "quantity": 2
    }
  ]
}
```

The basket includes a `totalPrice()` method that calculates the sum of all items (unitPrice × quantity).

### Error Responses

- `400 Bad Request`: validation errors (e.g., quantity < 1)
  ```json
  {"message": "Validation failed", "reasons": ["quantity must be at least 1"]}
  ```
- `401 Unauthorized`: missing or invalid JWT
- `404 Not Found`: menu item or basket item not found
  ```json
  {"message": "Menu item not found"}
  ```

## Event Integration

The service consumes `OrderCreatedEvent` from RabbitMQ to clear baskets after successful order creation.

- Exchange: `foodies`
- Routing Key: `order.created`
- Queue: `basket.order-created`
- Event handler: `orderCreatedEventConsumer` in `events/OrderCreatedEventHandler.kt`

## Architecture

### Components

- **BasketModule**: Dependency injection container wiring all services
- **BasketService**: Core business logic with Redis-backed implementation
- **BasketRepository**: Data access layer using Redis with JSON serialization
- **MenuClient**: HTTP client for fetching menu item details
- **Event Handler**: RabbitMQ consumer for `OrderCreatedEvent`
- **Domain Models**: `CustomerBasket`, `BasketItem`, and DTOs

### Service-Specific Stack

- **Persistence**: Redis via Lettuce client with coroutines support
- **Authentication**: JWT validation against Keycloak

## Running Locally

```bash
# Start Redis (required)
docker run -d -p 6379:6379 redis:7-alpine

# Start Menu service (required for adding items)
./gradlew :menu:run

# Start Keycloak (required for authentication)
cd webapp && docker compose up keycloak -d

# Start RabbitMQ (required for event handling)
docker run -d -p 5672:5672 -p 15672:15672 --name rabbitmq rabbitmq:3-management

# Run basket service
./gradlew :basket:run
```

## Testing

Test suites use TestContainers with real Redis:

- **BasketContractSpec**: End-to-end HTTP contract tests
- **BasketServiceSpec**: Business logic tests
- **BasketRoutesSpec**: HTTP route integration tests
- **RedisBasketRepositorySpec**: Redis repository tests
- **BasketValidationSpec**: Input validation tests
- **OrderCreatedEventHandlerSpec**: Event handling tests

## Example curl Commands

```bash
# Get basket (requires valid JWT token)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8083/basket

# Add item to basket
curl -X POST http://localhost:8083/basket/items \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"menuItemId": 1, "quantity": 2}'

# Update item quantity
curl -X PUT http://localhost:8083/basket/items/{itemId} \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"quantity": 5}'

# Remove item
curl -X DELETE http://localhost:8083/basket/items/{itemId} \
  -H "Authorization: Bearer $TOKEN"

# Clear basket
curl -X DELETE http://localhost:8083/basket \
  -H "Authorization: Bearer $TOKEN"

# Health checks
curl http://localhost:8083/healthz/startup
curl http://localhost:8083/healthz/liveness
curl http://localhost:8083/healthz/readiness
```

## Kubernetes Deployment

Deployed with standard resource limits (256Mi-512Mi memory, 100m-500m CPU) and health probes. See [k8s/README.md](../k8s/README.md) for details.

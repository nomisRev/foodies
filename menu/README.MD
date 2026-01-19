# Menu Service

Restaurant menu management with inventory tracking and event-driven integration. Provides CRUD operations for menu items while maintaining stock levels and coordinating with other services through message events.

> For common architectural patterns, testing approach, and build commands, see the [main README](../README.md#architecture-patterns).

## Overview

The menu service serves as the authoritative source for menu data and stock availability within the food ordering system. It integrates with order processing through event-driven architecture to ensure accurate inventory management and prevent overselling.

## Database Schema

**Table**: `menu_items`
- `id` (UUID, Primary Key) - Unique identifier
- `name` (String) - Menu item name with search index
- `description` (String) - Detailed description
- `image_url` (String) - URL to item image
- `price` (Decimal) - Item price (must be > 0)
- `stock` (Integer) - Available quantity for sale
- `created_at` (Timestamp) - Creation timestamp
- `updated_at` (Timestamp) - Last update timestamp

## REST API

All routes live under `/menu` and respond with JSON:

### Menu Item Management
- `GET /menu?offset&limit`: Paginated list of items
  - Defaults: `offset=0`, `limit=20` (max `50`)
  - Returns items with full details and stock information
- `GET /menu/{id}`: Fetch a single item by UUID
  - Returns `404` when item does not exist
- `POST /menu`: Create a new menu item
  - Required fields: `name`, `description`, `imageUrl`, `price`
  - Validation: `price` must be > 0, all fields non-empty
  - Returns created item with generated UUID and `stock=0`
- `PUT /menu/{id}`: Update existing menu item
  - Can update any subset of fields except `id`
  - Returns `404` if item does not exist
- `DELETE /menu/{id}`: Delete menu item by UUID
  - Returns `204` on success, `404` if item not found

### Health Monitoring
- `GET /healthz`: Comprehensive health probe
  - `startup`: Service initialization status
  - `liveness`: Service running status
  - `readiness`: Service ready to handle requests

## Event-Driven Integration

### Events Consumed
- `OrderAwaitingValidationEvent`: Validates stock availability and reserves items for orders
- `StockReturnedEvent`: Returns stock to inventory when orders are cancelled

### Events Published
- `StockConfirmedEvent`: Confirms successful stock reservation for valid orders
- `StockRejectedEvent`: Rejects orders with insufficient stock

### Message Queue Configuration
- **Exchange**: `foodies` (topic exchange)
- **Routing**: Events use topic-based routing for selective consumption
- **Connection**: RabbitMQ on localhost:5672 (configurable)

## Configuration

Values come from `config` in `src/main/resources/application.yaml` (environment overrides shown in parentheses):

### Server Configuration
- `HOST` / `PORT`: Bind address and port (default `0.0.0.0:8082`)

### Database Configuration
- `DB_URL`: PostgreSQL connection URL (default `jdbc:postgresql://localhost:5433/foodies-menu-database`)
- `DB_USERNAME`: Database username (default `foodies_admin`)
- `DB_PASSWORD`: Database password (default `foodies_password`)

### Message Queue Configuration
- `RABBITMQ_HOST`: RabbitMQ server host (default `localhost`)
- `RABBITMQ_PORT`: RabbitMQ server port (default `5672`)
- `RABBITMQ_USERNAME`: RabbitMQ username (default `guest`)
- `RABBITMQ_PASSWORD`: RabbitMQ password (default `guest`)

### Monitoring Configuration
- `OTEL_EXPORTER_OTLP_ENDPOINT`: OpenTelemetry endpoint (optional)
- `OTEL_SERVICE_NAME`: Service name for tracing (default `menu`)

## Running Locally

### Prerequisites
- Java 17+ installed
- PostgreSQL running on localhost:5433
- RabbitMQ running on localhost:5672 (for event functionality)

### Development Setup
```bash
# Start the service with default configuration
./gradlew :menu:run

# Or with custom database connection
DB_URL=jdbc:postgresql://localhost:5432/my-menu-db \
DB_USERNAME=myuser \
DB_PASSWORD=mypass \
./gradlew :menu:run
```

### Database Initialization
The service automatically runs Flyway migrations on startup to create and update the database schema. Sample data includes 24 Italian restaurant items across 5 categories.

## Testing

Test suites use TestContainers with real PostgreSQL and RabbitMQ:

- Service layer tests
- Repository tests
- Event handling tests
- Validation tests
- Health check tests

## Business Logic

### Stock Management
- **Atomic Operations**: Stock validation and reservation are atomic to prevent race conditions
- **Concurrency**: Safe handling of concurrent stock updates
- **Reservation**: Stock is temporarily reserved during order validation

# Profile Service

User profile management service that processes lifecycle events from Keycloak and maintains profile data in PostgreSQL. Operates as an event-driven consumer, handling user registration, profile updates, and account deletion through RabbitMQ messaging.

> For common architectural patterns, testing approach, and build commands, see the [main README](../README.md#architecture-patterns).

## Architecture

```
┌─────────────────┐    Event     ┌─────────────────┐
│    Keycloak     │ ──────────→  │   RabbitMQ     │
│  (Auth Service) │              │    (Message    │
│                 │              │     Broker)     │
└─────────────────┘              └─────────────────┘
                                          │
                                          ▼
┌─────────────────┐    Consumes    ┌─────────────────┐
│  Profile        │ ◄───────────── │   Profile       │
│  Service        │                │   Service       │
│  (Port 8081)    │                │   (Consumer)    │
└─────────────────┘                └─────────────────┘
                                          │
                                          ▼
                                ┌─────────────────┐
                                │   PostgreSQL    │
                                │   (Database)    │
                                └─────────────────┘
```

## Configuration

Configuration is loaded from `src/main/resources/application.yaml` under the `config` key. All values can be overridden via environment variables:

### Server Configuration
- `HOST`: Server bind address (default: `0.0.0.0`)
- `PORT`: Server port (default: `8081`)

### Database Configuration
- `DB_URL`: PostgreSQL JDBC URL (default: `jdbc:postgresql://localhost:5432/foodies-profile-database`)
- `DB_USERNAME`: Database username (default: `foodies_admin`)
- `DB_PASSWORD`: Database password (default: `foodies_password`)

### RabbitMQ Configuration
- `RABBITMQ_HOST`: RabbitMQ host (default: `localhost`)
- `RABBITMQ_PORT`: RabbitMQ port (default: `5672`)
- `RABBITMQ_USERNAME`: RabbitMQ username (default: `guest`)
- `RABBITMQ_PASSWORD`: RabbitMQ password (default: `guest`)
- `RABBITMQ_QUEUE`: Queue name for user events (default: `profile.registration`)

### Observability Configuration
- `OTEL_EXPORTER_OTLP_ENDPOINT`: OpenTelemetry collector endpoint (default: `http://localhost:4317`)

## Data Model

### Profile Table Schema

```sql
CREATE TABLE profiles (
    id BIGSERIAL PRIMARY KEY,
    subject VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL
);
```

### Profile Entity

```kotlin
data class Profile(
    val id: Long,
    val subject: String,      // Keycloak subject identifier
    val email: String,
    val firstName: String,
    val lastName: String,
)
```

## Event Processing

The service consumes `UserEvent` messages from RabbitMQ and processes them idempotently:

### UserEvent Types

#### Registration Event
```kotlin
data class Registration(
    override val subject: String,
    val email: String,
    val firstName: String,
    val lastName: String
) : UserEvent
```
- **Action**: Creates new profile if subject doesn't exist
- **Idempotency**: Uses `insertOrIgnore` to handle duplicate registrations

#### UpdateProfile Event
```kotlin
data class UpdateProfile(
    override val subject: String,
    val email: String,
    val firstName: String,
    val lastName: String
) : UserEvent
```
- **Action**: Updates existing profile or creates if doesn't exist
- **Idempotency**: Uses `upsert` operation

#### Delete Event
```kotlin
data class Delete(override val subject: String) : UserEvent
```
- **Action**: Removes profile by subject
- **Returns**: Boolean indicating if a profile was deleted

### Message Processing

- **Exchange**: Default exchange (`""`)
- **Queue**: `profile.registration` (configurable)
- **Processing**: Parallel message consumption with manual ack/nack
- **Error Handling**: Messages are nacked on failure for retry
- **Logging**: Structured logging for all processed events

## Runtime Dependencies

- **PostgreSQL**: Database for profile storage
- **RabbitMQ**: Message broker for event consumption

## Running Locally

### Prerequisites

1. **PostgreSQL** running and accessible
2. **RabbitMQ** running and accessible
3. **JDK 21** installed

### Start Dependencies

```bash
# Start PostgreSQL (using Docker)
docker run -d \
  --name postgres-profile \
  -e POSTGRES_DB=foodies-profile-database \
  -e POSTGRES_USER=foodies_admin \
  -e POSTGRES_PASSWORD=foodies_password \
  -p 5432:5432 \
  postgres:18-alpine

# Start RabbitMQ (using Docker)
docker run -d \
  --name rabbitmq \
  -p 5672:5672 \
  -p 15672:15672 \
  rabbitmq:4.2-management
```

### Run the Service

```bash
./gradlew :profile:run
```

The service will:
1. Apply database migrations automatically
2. Connect to RabbitMQ
3. Start consuming user events
4. Expose health check endpoints on port 8081

### Verify Operation

```bash
# Check service health
curl http://localhost:8081/healthz/readiness

# Check database connectivity
docker exec -it postgres-profile psql -U foodies_admin -d foodies-profile-database -c "SELECT COUNT(*) FROM profiles;"

# Check RabbitMQ queue status
curl -u guest:guest http://localhost:15672/api/queues/%2f/profile.registration
```

## Database Migrations

Migrations are managed by Flyway and located in `src/main/resources/db/migration/`:

### Current Migrations
- `V1__create_profile_table.sql`: Creates the initial profiles table with unique constraint on subject

### Migration Process
- Migrations run automatically on service startup
- Flyway tracks applied migrations in the `flyway_schema_history` table
- Failed migrations prevent service startup

## Testing

Test suites use TestContainers with real PostgreSQL and RabbitMQ:

- **ProfileRepositorySpec**: Repository tests
- Integration tests for event consumers


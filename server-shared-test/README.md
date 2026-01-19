# Server Shared Test

Shared testing utilities library for the Foodies server application. Provides common testing infrastructure, fixtures, and utilities to facilitate integration testing across all services.

> For common architectural patterns and testing guidelines, see the [main README](../README.md#architecture-patterns).

## Dependencies

### Core Dependencies
- **server-shared**: Main server module dependencies
- **Ktor**: Server and client testing components
- **TestBalloon**: Test framework and execution scope
- **TestContainers**: Container-based testing for PostgreSQL and RabbitMQ
- **Database**: PostgreSQL, HikariCP, and Exposed ORM

### Testing Dependencies
- `ktor-server-test-host`: Ktor server testing utilities
- `testcontainers-postgresql`: PostgreSQL container support
- `testcontainers-rabbitmq`: RabbitMQ container support
- `testballoon`: TestBalloon framework integration

## Components

### TestApplication (`TestApplication.kt`)

Provides enhanced Ktor application testing with TestBalloon integration.

#### Functions
- `testApplication(name: String, block: suspend ApplicationTestBuilder.() -> Unit)`: Creates a test application with TestBalloon registration
- `ApplicationTestBuilder.jsonClient()`: Creates a pre-configured HTTP client with JSON content negotiation

#### Usage
```kotlin
@TestRegistering
fun MySuite.testUserEndpoints() = testApplication("user endpoints") {
    val client = jsonClient()
    // Test your endpoints here
}
```

### PostgreSQLContainer (`PostgreSQLContainer.kt`)

Provides PostgreSQL TestContainer fixtures with database connectivity.

#### Classes
- `PostgreSQLContainer`: Custom PostgreSQL container using `postgres:18-alpine`

#### Functions
- `TestSuite.postgresContainer()`: Creates a PostgreSQL container fixture
- `PostgreSQLContainer.dataSource()`: Creates a DataSource fixture from the container
- `PostgreSQLContainer.hikariDataSource()`: Creates HikariCP data source
- `HikariDataSource.database()`: Creates Exposed database connection

#### Usage
```kotlin
@TestRegistering
fun MySuite.testDatabaseOperations() = test("database operations") {
    val postgres = postgresContainer()
    val dataSource = postgres.dataSource()
    val database = dataSource.database()
    
    // Use database for testing
}
```

### RabbitMQContainer (`RabbitMQContainer.kt`)

Provides RabbitMQ TestContainer fixtures for message queue testing.

#### Classes
- `RabbitContainer`: Custom RabbitMQ container using `rabbitmq:4.2.2-alpine`

#### Functions
- `TestSuite.rabbitContainer()`: Creates a RabbitMQ container fixture
- `RabbitContainer.connectionFactory()`: Creates RabbitMQ connection factory
- `ConnectionFactory.channel(block: (Channel) -> A)`: Extension for safe channel operations

#### Usage
```kotlin
@TestRegistering
fun MySuite.testMessagePublishing() = test("message publishing") {
    val rabbit = rabbitContainer()
    val factory = rabbit.connectionFactory()
    
    factory.channel { channel ->
        // Publish/consume messages
    }
}
```

### Eventually (`Eventually.kt`)

Provides utility for eventual consistency testing with retry logic.

#### Functions
- `eventually(timeout: Duration = 3.seconds, block: () -> Unit)`: Retries block until success or timeout

#### Usage
```kotlin
@TestRegistering
fun MySuite.testEventualConsistency() = test("eventual consistency") {
    // Trigger async operation
    
    eventually {
        // Assert eventual state
        assertEquals("expected", getCurrentState())
    }
}
```

### TestSuiteWithContext (`TestSuiteWithContext.kt`)

Provides TestBalloon test suite creation with context support.

#### Functions
- `ctxSuite(name: String, context: TestSuite.() -> A, content: context(A) TestSuite.() -> Unit)`: Creates test suite with shared context

#### Usage
```kotlin
@TestRegistering
fun createTestSuite() = ctxSuite("my feature suite") {
    // Setup shared context
    SharedTestContext()
} content { context ->
    test("test 1") {
        // Use context
    }
    
    test("test 2") {
        // Use same context
    }
}
```

## Integration Patterns

### Database Testing
```kotlin
val postgres = postgresContainer()
val dataSource = postgres.dataSource()
val database = dataSource.database()
```

### Message Queue Testing
```kotlin
val rabbit = rabbitContainer()
val factory = rabbit.connectionFactory()
factory.channel { channel -> /* publish/consume */ }
```

### Application Testing
```kotlin
testApplication("my test") {
    val client = jsonClient()
    // Test endpoints
}
```

### Eventual Consistency
```kotlin
eventually {
    assertEquals("expected", getCurrentState())
}
```

## Configuration

Default container versions:
- PostgreSQL: `postgres:18-alpine`
- RabbitMQ: `rabbitmq:4.2.2-alpine`
- Eventually timeout: `3.seconds`
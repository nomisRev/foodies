# Profile Service Resilience Specification

## Overview

Improve the reliability and fault tolerance of the profile service's RabbitMQ event consumption. The current implementation uses a simple infinite retry with a fixed 1-second delay. This specification outlines production-ready resilience patterns.

## Current State

### Current Retry Logic

```kotlin
// UserEventConsumer.kt:38-42
.retry { e ->
    delay(1000) // TODO: Introduce proper resilience schedule
    logger.error("Failed to process user message, retrying", e)
    true // Retry and continue processing forever
}
```

### Issues with Current Approach

1. **No exponential backoff**: Fixed 1-second delay doesn't prevent thundering herd
2. **No max retry limit**: Poison messages retry forever, blocking the queue
3. **No dead-letter queue**: Failed messages have nowhere to go for inspection
4. **No circuit breaker**: Database failures can cause cascading failures
5. **No distinction between transient and permanent failures**
6. **No metrics/alerting**: Failures are logged but not tracked

## Proposed Resilience Patterns

### 1. Exponential Backoff with Jitter

Replace fixed delay with exponential backoff to prevent thundering herd:

```kotlin
data class RetryConfig(
    val initialDelay: Duration = 1.seconds,
    val maxDelay: Duration = 60.seconds,
    val multiplier: Double = 2.0,
    val jitterFactor: Double = 0.1,
    val maxRetries: Int = 5,
)

fun calculateDelay(attempt: Int, config: RetryConfig): Duration {
    val exponentialDelay = config.initialDelay * config.multiplier.pow(attempt)
    val cappedDelay = minOf(exponentialDelay, config.maxDelay)
    val jitter = (Random.nextDouble() - 0.5) * 2 * config.jitterFactor * cappedDelay.inWholeMilliseconds
    return (cappedDelay.inWholeMilliseconds + jitter.toLong()).milliseconds
}
```

### 2. Retry Classification

Distinguish between retryable and non-retryable errors:

```kotlin
sealed interface ProcessingResult {
    data object Success : ProcessingResult
    data class RetryableError(val cause: Exception) : ProcessingResult
    data class PermanentError(val cause: Exception) : ProcessingResult
}

fun classifyError(e: Exception): ProcessingResult = when (e) {
    // Transient - retry
    is java.net.SocketTimeoutException -> ProcessingResult.RetryableError(e)
    is java.sql.SQLTransientConnectionException -> ProcessingResult.RetryableError(e)
    is org.postgresql.util.PSQLException -> {
        if (e.sqlState?.startsWith("08") == true) // Connection errors
            ProcessingResult.RetryableError(e)
        else
            ProcessingResult.PermanentError(e)
    }
    // Permanent - don't retry
    is kotlinx.serialization.SerializationException -> ProcessingResult.PermanentError(e)
    is IllegalArgumentException -> ProcessingResult.PermanentError(e)
    // Default to retry for unknown errors
    else -> ProcessingResult.RetryableError(e)
}
```

### 3. Dead-Letter Queue (DLQ)

Configure RabbitMQ with a dead-letter exchange for failed messages:

#### RabbitMQ Configuration

```kotlin
// Queue declaration with DLQ
fun Channel.declareQueuesWithDLQ(queueName: String) {
    val dlqName = "$queueName.dlq"
    val dlxName = "$queueName.dlx"

    // Declare dead-letter exchange
    exchangeDeclare(dlxName, "direct", true)

    // Declare dead-letter queue
    queueDeclare(dlqName, true, false, false, null)
    queueBind(dlqName, dlxName, queueName)

    // Declare main queue with DLQ settings
    val args = mapOf(
        "x-dead-letter-exchange" to dlxName,
        "x-dead-letter-routing-key" to queueName,
    )
    queueDeclare(queueName, true, false, false, args)
}
```

#### Application Configuration

```yaml
# application.yaml
config:
  rabbit:
    queue: "$RABBITMQ_QUEUE:profile.registration"
    dlq:
      enabled: true
      max_retries: 5
      ttl_seconds: 86400  # 24 hours before DLQ messages expire
```

### 4. Enhanced Consumer Implementation

```kotlin
data class ResilienceConfig(
    val retry: RetryConfig = RetryConfig(),
    val dlqEnabled: Boolean = true,
)

fun userEventConsumer(
    messages: Flow<Message<UserEvent>>,
    profileRepository: ProfileRepository,
    config: ResilienceConfig = ResilienceConfig(),
    metrics: ConsumerMetrics = NoOpMetrics,
) = Consumer {
    messages.map { message ->
        processWithResilience(message, profileRepository, config, metrics)
    }
}

private suspend fun processWithResilience(
    message: Message<UserEvent>,
    repository: ProfileRepository,
    config: ResilienceConfig,
    metrics: ConsumerMetrics,
) {
    var attempt = 0
    var lastError: Exception? = null

    while (attempt <= config.retry.maxRetries) {
        try {
            processEvent(message.value, repository)
            message.ack()
            metrics.recordSuccess(message.value)
            return
        } catch (e: Exception) {
            lastError = e
            when (val result = classifyError(e)) {
                is ProcessingResult.PermanentError -> {
                    logger.error("Permanent error processing message, sending to DLQ", e)
                    metrics.recordPermanentFailure(message.value, e)
                    message.nack() // Will route to DLQ if configured
                    return
                }
                is ProcessingResult.RetryableError -> {
                    attempt++
                    if (attempt > config.retry.maxRetries) {
                        logger.error("Max retries exceeded, sending to DLQ", e)
                        metrics.recordMaxRetriesExceeded(message.value, e)
                        message.nack()
                        return
                    }
                    val delay = calculateDelay(attempt, config.retry)
                    logger.warn("Retryable error, attempt $attempt/${config.retry.maxRetries}, waiting $delay", e)
                    metrics.recordRetry(message.value, attempt)
                    delay(delay)
                }
                ProcessingResult.Success -> error("Unreachable")
            }
        }
    }
}

private fun processEvent(event: UserEvent, repository: ProfileRepository) {
    when (event) {
        is UserEvent.Registration -> {
            repository.insertOrIgnore(event.subject, event.email, event.firstName, event.lastName)
            logger.info("Processed registration for subject ${event.subject}")
        }
        is UserEvent.UpdateProfile -> {
            repository.upsert(event.subject, event.email, event.firstName, event.lastName)
            logger.info("Processed profile update for subject ${event.subject}")
        }
        is UserEvent.Delete -> {
            repository.deleteBySubject(event.subject)
            logger.info("Processed deletion for subject ${event.subject}")
        }
    }
}
```

### 5. Circuit Breaker Pattern

Prevent cascading failures when the database is unavailable:

```kotlin
class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val resetTimeout: Duration = 30.seconds,
) {
    private val failures = AtomicInteger(0)
    private val state = AtomicReference(State.CLOSED)
    private val lastFailureTime = AtomicLong(0)

    enum class State { CLOSED, OPEN, HALF_OPEN }

    suspend fun <T> execute(block: suspend () -> T): T {
        return when (state.get()) {
            State.OPEN -> {
                if (shouldAttemptReset()) {
                    state.set(State.HALF_OPEN)
                    tryExecution(block)
                } else {
                    throw CircuitBreakerOpenException()
                }
            }
            State.HALF_OPEN, State.CLOSED -> tryExecution(block)
        }
    }

    private suspend fun <T> tryExecution(block: suspend () -> T): T {
        return try {
            val result = block()
            onSuccess()
            result
        } catch (e: Exception) {
            onFailure()
            throw e
        }
    }

    private fun onSuccess() {
        failures.set(0)
        state.set(State.CLOSED)
    }

    private fun onFailure() {
        lastFailureTime.set(System.currentTimeMillis())
        if (failures.incrementAndGet() >= failureThreshold) {
            state.set(State.OPEN)
        }
    }

    private fun shouldAttemptReset(): Boolean {
        return System.currentTimeMillis() - lastFailureTime.get() >= resetTimeout.inWholeMilliseconds
    }
}

class CircuitBreakerOpenException : Exception("Circuit breaker is open")
```

### 6. Metrics and Monitoring

```kotlin
interface ConsumerMetrics {
    fun recordSuccess(event: UserEvent)
    fun recordRetry(event: UserEvent, attempt: Int)
    fun recordPermanentFailure(event: UserEvent, error: Exception)
    fun recordMaxRetriesExceeded(event: UserEvent, error: Exception)
    fun recordCircuitBreakerOpen()
}

// Micrometer implementation
class MicrometerConsumerMetrics(private val registry: MeterRegistry) : ConsumerMetrics {
    private val successCounter = registry.counter("profile.consumer.success")
    private val retryCounter = registry.counter("profile.consumer.retry")
    private val permanentFailureCounter = registry.counter("profile.consumer.permanent_failure")
    private val dlqCounter = registry.counter("profile.consumer.dlq")
    private val circuitBreakerCounter = registry.counter("profile.consumer.circuit_breaker_open")

    override fun recordSuccess(event: UserEvent) {
        successCounter.increment()
        registry.counter("profile.consumer.success", "event_type", event::class.simpleName).increment()
    }

    override fun recordRetry(event: UserEvent, attempt: Int) {
        retryCounter.increment()
        registry.counter("profile.consumer.retry", "attempt", attempt.toString()).increment()
    }

    override fun recordPermanentFailure(event: UserEvent, error: Exception) {
        permanentFailureCounter.increment()
        dlqCounter.increment()
    }

    override fun recordMaxRetriesExceeded(event: UserEvent, error: Exception) {
        dlqCounter.increment()
    }

    override fun recordCircuitBreakerOpen() {
        circuitBreakerCounter.increment()
    }
}
```

### 7. Health Checks

Add health indicators for resilience components:

```kotlin
data class ConsumerHealth(
    val status: Status,
    val circuitBreakerState: String,
    val dlqSize: Long,
    val lastProcessedAt: Instant?,
    val failureRate: Double,
) {
    enum class Status { UP, DOWN, DEGRADED }
}

fun Route.consumerHealthRoutes(
    circuitBreaker: CircuitBreaker,
    channel: Channel,
    dlqName: String,
) {
    get("/healthz/consumer") {
        val dlqSize = channel.messageCount(dlqName)
        val health = ConsumerHealth(
            status = when {
                circuitBreaker.state == CircuitBreaker.State.OPEN -> ConsumerHealth.Status.DOWN
                dlqSize > 100 -> ConsumerHealth.Status.DEGRADED
                else -> ConsumerHealth.Status.UP
            },
            circuitBreakerState = circuitBreaker.state.name,
            dlqSize = dlqSize,
            lastProcessedAt = lastProcessedTimestamp.get(),
            failureRate = calculateFailureRate(),
        )

        if (health.status == ConsumerHealth.Status.UP) {
            call.respond(health)
        } else {
            call.respond(HttpStatusCode.ServiceUnavailable, health)
        }
    }
}
```

## Configuration

### Full Configuration Schema

```yaml
config:
  rabbit:
    host: "$RABBITMQ_HOST:localhost"
    port: "$RABBITMQ_PORT:5672"
    username: "$RABBITMQ_USERNAME:guest"
    password: "$RABBITMQ_PASSWORD:guest"
    queue: "$RABBITMQ_QUEUE:profile.registration"

  resilience:
    retry:
      initial_delay_ms: 1000
      max_delay_ms: 60000
      multiplier: 2.0
      jitter_factor: 0.1
      max_retries: 5

    circuit_breaker:
      failure_threshold: 5
      reset_timeout_seconds: 30

    dlq:
      enabled: true
      ttl_seconds: 86400

  metrics:
    enabled: true
```

## Implementation Steps

### Phase 1: Retry Improvements
1. Implement exponential backoff with jitter
2. Add error classification (retryable vs permanent)
3. Add max retry limit
4. Update configuration schema

### Phase 2: Dead-Letter Queue
1. Update RabbitMQ configuration in Docker Compose
2. Update Kubernetes ConfigMaps for RabbitMQ
3. Implement DLQ queue declaration in code
4. Update consumer to route failed messages to DLQ

### Phase 3: Circuit Breaker
1. Implement circuit breaker pattern
2. Integrate with consumer
3. Add circuit breaker state to health checks

### Phase 4: Observability
1. Add metrics interface
2. Implement Micrometer metrics (optional dependency)
3. Add DLQ monitoring
4. Create Grafana dashboard (if applicable)

### Phase 5: DLQ Processing
1. Create DLQ consumer/inspector tool
2. Add replay capability for DLQ messages
3. Add manual ack/purge for DLQ

## Testing Requirements

### Unit Tests
- Exponential backoff calculation
- Error classification logic
- Circuit breaker state transitions

### Integration Tests
- Retry behavior with simulated failures
- DLQ routing for permanent failures
- Circuit breaker opening/closing
- Message replay from DLQ

### Chaos Testing
- Database connection failures
- RabbitMQ connection drops
- High latency scenarios

## Comparison with eShop

| Feature | eShop | Foodies (Current) | Foodies (Proposed) |
|---------|-------|-------------------|-------------------|
| Event System | In-process | RabbitMQ | RabbitMQ |
| Retry Strategy | N/A (sync) | Infinite fixed delay | Exponential backoff |
| Max Retries | N/A | None | Configurable (default: 5) |
| Dead-Letter Queue | N/A | None | Yes |
| Circuit Breaker | N/A | None | Yes |
| Metrics | IdentityServer events | Logging only | Micrometer metrics |
| Health Checks | Standard ASP.NET | `/healthz` basic | Extended consumer health |

## Open Questions

1. Should we use a library like Resilience4j/Kotlin instead of custom implementation?
2. What alerting thresholds should trigger PagerDuty/Slack notifications?
3. Should DLQ messages be persisted to a separate database for analysis?
4. Do we need a UI for DLQ management or is CLI tooling sufficient?

## References

- [RabbitMQ Dead Letter Exchanges](https://www.rabbitmq.com/dlx.html)
- [Circuit Breaker Pattern](https://martinfowler.com/bliki/CircuitBreaker.html)
- [Exponential Backoff](https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/)
- [Resilience4j Kotlin](https://resilience4j.readme.io/docs/getting-started-3)

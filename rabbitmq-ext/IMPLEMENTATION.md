# RabbitMQ DLX + TTL support in rabbitmq-ext

## Goal
Add dead-letter exchange (DLX) and message TTL support to `rabbitmq-ext` in a way that is type-safe and mostly transparent to callers. Default behavior should enable DLX for all queues, while allowing per-queue overrides (disable, custom TTL, custom DLX routing).

## Current state
- `RabbitMQSubscriber.subscribe` defaults to `queueDeclare(queueName, true, false, false, null)`.
- Callers often pass their own `queueDeclare` in `configure` to bind routing keys.
- No shared, typed configuration for queue args or dead-lettering.

## Proposed design

### Topology
- Single main exchange: `foodies` (existing)
- DLX exchange: `${exchange}.dlx` (defaults to `foodies.dlx`)
- Per-queue DLQs: `{queueName}.dlq`

All resources are declared from code at runtime (no K8s CRDs for queues/exchanges).

### Types

```kotlin
sealed interface RetryPolicy {
    data object None : RetryPolicy
    data class MaxAttempts(val value: Int) : RetryPolicy
}

sealed interface DeadLetterPolicy {
    data class Enabled(
        val exchange: (original: String) -> String = { "$it.dlx" },
        val routingKey: (original: String) -> String = { "$it.dlq" }
    ) : DeadLetterPolicy

    data object Disabled : DeadLetterPolicy
}

class QueueOptionsBuilder<A> {
    var durable: Boolean = true
    var ttl: Duration? = null
    var retry: RetryPolicy = RetryPolicy.MaxAttempts(5)
    var deadLetter: DeadLetterPolicy = DeadLetterPolicy.Enabled()
}
```

### API

```kotlin
fun <A> subscribe(
    queueName: String,
    routingKey: RoutingKey<A>,
    configure: QueueOptionsBuilder<A>.() -> Unit = {}
): Flow<A>
```

### Usage examples

```kotlin
// Simple - DLX enabled by default, no TTL
subscriber.subscribe("order-service.order-placed", OrderPlaced.routingKey)

// With TTL
subscriber.subscribe("order-service.order-placed", OrderPlaced.routingKey) {
    ttl = 5.minutes
}

// Disable DLX for specific queue
subscriber.subscribe("order-service.order-placed", OrderPlaced.routingKey) {
    deadLetter = DeadLetterPolicy.Disabled
}

```

### Default behavior
- DLX enabled by default, TTL disabled by default (`null`)
- When DLX enabled:
  - `x-dead-letter-exchange` set to `${exchange}.dlx`
  - `x-dead-letter-routing-key` set to `{queueName}.dlq`
- When TTL set:
  - `x-message-ttl` set to duration in milliseconds
- DLX exchange and DLQ declared automatically at subscribe time

### Retry handling (transparent to consumers)
- RabbitMQ does not support "max attempts then DLQ" natively; attempts are tracked via `x-death` or custom headers.
- `Message` should compute `deliveryAttempts` from `x-death` and expose it as a property.
- When `retry` is `MaxAttempts(n)`, `Message.nack()` should check `deliveryAttempts` before deciding to dead-letter.
  - If attempts < n, requeue via the configured retry mechanism (future: retry queues with TTL + DLX).
  - If attempts >= n, nack without requeue to send to DLQ.
- This keeps retry logic centralized and callers can continue to call `ack()` / `nack()` without inspecting headers.

### Queue declaration (internal)
At subscribe time, when DLX is enabled:
1. Declare DLX exchange (`${exchange}.dlx`, topic, durable)
2. Declare DLQ queue (`{queueName}.dlq`, durable)
3. Bind DLQ to DLX with routing key = `{queueName}.dlq`
4. Declare main queue with DLX arguments
5. Bind main queue to main exchange with routing key

## Epics
Epics are designed to be implemented in parallel. Detailed task lists live in `rabbitmq-ext/TODO.md`.

1. **Queue options API**: new types, defaults, and subscribe signatures.
2. **Queue declaration**: DLX/DLQ topology and arguments for main queue.
3. **Retry handling**: `Message` attempts tracking and `nack()` behavior.
4. **Tests**: TestContainers coverage for topology, args, and retry.

## Decisions made
- **Default TTL**: `null` (no TTL) - can change project-wide default later
- **DLX topology**: Single `foodies.dlx` exchange with per-queue DLQs
- **DLQ naming**: `{queueName}.dlq`
- **Queue name**: Mandatory parameter, not derived
- **Resilience/retry**: Deferred to future work
- **Max attempts**: Not modeled as TTL; use `RetryPolicy.MaxAttempts` with `x-death` tracking instead

## Notes
- Type parameter `<A>` on `QueueOptionsBuilder` ensures all bindings match the message type
- DLQs are durable and non-exclusive
- Existing queues without DLX args will need manual deletion if args change (no prod data currently)

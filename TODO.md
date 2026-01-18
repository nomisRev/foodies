# OpenTelemetry Configuration - Required Changes

## Problem
All services currently use a hardcoded `http://localhost:4317` as the OpenTelemetry OTLP endpoint. This causes connection failures when the OTEL collector is not running on localhost or is at a different endpoint.

## Solution
Make the OTLP endpoint configurable through `application.yaml` with environment variable support, following the existing configuration pattern used for database and RabbitMQ settings.

---

## Changes Required

### 1. Profile Service

#### File: `profile/src/main/kotlin/io/ktor/foodies/server/Config.kt`
Add telemetry configuration to the Config data class:

```kotlin
@Serializable
data class Config(
    val host: String,
    val port: Int,
    @SerialName("data_source") val dataSource: DataSource.Config,
    val rabbit: Rabbit,
    val telemetry: Telemetry,  // ADD THIS
) {
    @Serializable
    data class Rabbit(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val queue: String,
    )

    // ADD THIS
    @Serializable
    data class Telemetry(
        @SerialName("otlp_endpoint") val otlpEndpoint: String,
    )
}
```

#### File: `profile/src/main/kotlin/io/ktor/foodies/server/ProfileApp.kt`
Update the main function to pass the endpoint:

```kotlin
fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        val openTelemetry = openTelemetry(config.telemetry.otlpEndpoint)  // CHANGE THIS LINE
        app(module(config, openTelemetry))
    }.start(wait = true)
}
```

#### File: `profile/src/main/resources/application.yaml`
Add telemetry section:

```yaml
config:
  host: "$HOST:0.0.0.0"
  port: "$PORT:8081"
  data_source:
    url: "$DB_URL:jdbc:postgresql://localhost:5432/foodies-profile-database"
    username: "$DB_USERNAME:foodies_admin"
    password: "$DB_PASSWORD:foodies_password"
  rabbit:
    host: "$RABBITMQ_HOST:localhost"
    port: "$RABBITMQ_PORT:5672"
    username: "$RABBITMQ_USERNAME:guest"
    password: "$RABBITMQ_PASSWORD:guest"
    queue: "$RABBITMQ_QUEUE:profile.registration"
  telemetry:  # ADD THIS SECTION
    otlp_endpoint: "$OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317"
```

---

### 2. Basket Service

#### File: `basket/src/main/kotlin/io/ktor/foodies/basket/Config.kt`
Add telemetry configuration (same pattern as Profile):

```kotlin
@Serializable
data class Config(
    val host: String,
    val port: Int,
    val redis: Redis,
    val telemetry: Telemetry,  // ADD THIS
) {
    @Serializable
    data class Redis(
        val host: String,
        val port: Int,
    )

    // ADD THIS
    @Serializable
    data class Telemetry(
        @SerialName("otlp_endpoint") val otlpEndpoint: String,
    )
}
```

#### File: `basket/src/main/kotlin/io/ktor/foodies/basket/BasketApp.kt`
Update the main function:

```kotlin
fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        val openTelemetry = openTelemetry(config.telemetry.otlpEndpoint)  // CHANGE THIS LINE
        app(module(config, openTelemetry))
    }.start(wait = true)
}
```

#### File: `basket/src/main/resources/application.yaml`
Add telemetry section:

```yaml
config:
  host: "$HOST:0.0.0.0"
  port: "$PORT:8082"
  redis:
    host: "$REDIS_HOST:localhost"
    port: "$REDIS_PORT:6379"
  telemetry:  # ADD THIS SECTION
    otlp_endpoint: "$OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317"
```

---

### 3. Menu Service

#### File: `menu/src/main/kotlin/io/ktor/foodies/menu/Config.kt`
Add telemetry configuration:

```kotlin
@Serializable
data class Config(
    val host: String,
    val port: Int,
    @SerialName("data_source") val dataSource: DataSource.Config,
    val telemetry: Telemetry,  // ADD THIS
) {
    // ADD THIS
    @Serializable
    data class Telemetry(
        @SerialName("otlp_endpoint") val otlpEndpoint: String,
    )
}
```

#### File: `menu/src/main/kotlin/io/ktor/foodies/menu/MenuApp.kt`
Update the main function:

```kotlin
fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        val openTelemetry = openTelemetry(config.telemetry.otlpEndpoint)  // CHANGE THIS LINE
        app(module(config, openTelemetry))
    }.start(wait = true)
}
```

#### File: `menu/src/main/resources/application.yaml`
Add telemetry section:

```yaml
config:
  host: "$HOST:0.0.0.0"
  port: "$PORT:8083"
  data_source:
    url: "$DB_URL:jdbc:postgresql://localhost:5432/foodies-menu-database"
    username: "$DB_USERNAME:foodies_admin"
    password: "$DB_PASSWORD:foodies_password"
  telemetry:  # ADD THIS SECTION
    otlp_endpoint: "$OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317"
```

---

### 4. Order Service

#### File: `order/src/main/kotlin/io/ktor/foodies/order/Config.kt`
Add telemetry configuration:

```kotlin
@Serializable
data class Config(
    val host: String,
    val port: Int,
    @SerialName("data_source") val dataSource: DataSource.Config,
    val rabbit: Rabbit,
    val telemetry: Telemetry,  // ADD THIS
) {
    @Serializable
    data class Rabbit(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
    )

    // ADD THIS
    @Serializable
    data class Telemetry(
        @SerialName("otlp_endpoint") val otlpEndpoint: String,
    )
}
```

#### File: `order/src/main/kotlin/io/ktor/foodies/order/OrderApp.kt`
Update the main function:

```kotlin
fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        val openTelemetry = openTelemetry(config.telemetry.otlpEndpoint)  // CHANGE THIS LINE
        app(module(config, openTelemetry))
    }.start(wait = true)
}
```

#### File: `order/src/main/resources/application.yaml`
Add telemetry section:

```yaml
config:
  host: "$HOST:0.0.0.0"
  port: "$PORT:8084"
  data_source:
    url: "$DB_URL:jdbc:postgresql://localhost:5432/foodies-order-database"
    username: "$DB_USERNAME:foodies_admin"
    password: "$DB_PASSWORD:foodies_password"
  rabbit:
    host: "$RABBITMQ_HOST:localhost"
    port: "$RABBITMQ_PORT:5672"
    username: "$RABBITMQ_USERNAME:guest"
    password: "$RABBITMQ_PASSWORD:guest"
  telemetry:  # ADD THIS SECTION
    otlp_endpoint: "$OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317"
```

---

### 5. Payment Service

#### File: `payment/src/main/kotlin/io/ktor/foodies/payment/Config.kt`
Add telemetry configuration:

```kotlin
@Serializable
data class Config(
    val host: String,
    val port: Int,
    @SerialName("data_source") val dataSource: DataSource.Config,
    val rabbit: Rabbit,
    val telemetry: Telemetry,  // ADD THIS
) {
    @Serializable
    data class Rabbit(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val queue: String,
    )

    // ADD THIS
    @Serializable
    data class Telemetry(
        @SerialName("otlp_endpoint") val otlpEndpoint: String,
    )
}
```

#### File: `payment/src/main/kotlin/io/ktor/foodies/payment/PaymentApp.kt`
Update the main function:

```kotlin
fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        val openTelemetry = openTelemetry(config.telemetry.otlpEndpoint)  // CHANGE THIS LINE
        app(module(config, openTelemetry))
    }.start(wait = true)
}
```

#### File: `payment/src/main/resources/application.yaml`
Add telemetry section:

```yaml
config:
  host: "$HOST:0.0.0.0"
  port: "$PORT:8085"
  data_source:
    url: "$DB_URL:jdbc:postgresql://localhost:5432/foodies-payment-database"
    username: "$DB_USERNAME:foodies_admin"
    password: "$DB_PASSWORD:foodies_password"
  rabbit:
    host: "$RABBITMQ_HOST:localhost"
    port: "$RABBITMQ_PORT:5672"
    username: "$RABBITMQ_USERNAME:guest"
    password: "$RABBITMQ_PASSWORD:guest"
    queue: "$RABBITMQ_QUEUE:payment.processing"
  telemetry:  # ADD THIS SECTION
    otlp_endpoint: "$OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317"
```

---

### 6. WebApp Service

#### File: `webapp/src/main/kotlin/io/ktor/foodies/server/Config.kt`
Add telemetry configuration:

```kotlin
@Serializable
data class Config(
    val host: String,
    val port: Int,
    val telemetry: Telemetry,  // ADD THIS
) {
    // ADD THIS
    @Serializable
    data class Telemetry(
        @SerialName("otlp_endpoint") val otlpEndpoint: String,
    )
}
```

#### File: `webapp/src/main/kotlin/io/ktor/foodies/server/WebApp.kt`
Update the main function:

```kotlin
fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        val openTelemetry = openTelemetry(config.telemetry.otlpEndpoint)  // CHANGE THIS LINE
        app(module(config, openTelemetry))
    }.start(wait = true)
}
```

#### File: `webapp/src/main/resources/application.yaml`
Add telemetry section:

```yaml
config:
  host: "$HOST:0.0.0.0"
  port: "$PORT:8080"
  telemetry:  # ADD THIS SECTION
    otlp_endpoint: "$OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317"
```

---

### 7. Kubernetes Deployments

For each service deployment, add the `OTEL_EXPORTER_OTLP_ENDPOINT` environment variable.

#### Files to update:
- `k8s/base/profile/deployment.yaml`
- `k8s/base/basket/deployment.yaml`
- `k8s/base/menu/deployment.yaml`
- `k8s/base/order/deployment.yaml`
- `k8s/base/payment/deployment.yaml`
- `k8s/base/webapp/deployment.yaml`

Add to the `env` section of each deployment:

```yaml
spec:
  template:
    spec:
      containers:
      - name: <service-name>
        env:
          # ... existing environment variables ...
          - name: OTEL_EXPORTER_OTLP_ENDPOINT
            value: "http://otel-collector:4317"  # Update with your actual OTEL collector service name
```

**Note:** Update `otel-collector:4317` to match your actual OpenTelemetry collector service name and port in your Kubernetes cluster.

---

## Testing

After making these changes:

1. **Local Testing:**
   ```bash
   # Run without OTEL collector (will use default localhost:4317)
   ./gradlew :profile:run

   # Run with custom OTEL endpoint
   OTEL_EXPORTER_OTLP_ENDPOINT=http://custom-host:4317 ./gradlew :profile:run
   ```

2. **Kubernetes Testing:**
   ```bash
   # Deploy to Kubernetes
   ./gradlew localDeployK8s

   # Verify environment variables are set
   kubectl get pods
   kubectl describe pod <pod-name>
   ```

3. **Verify Configuration:**
   - Check that services start without OTEL connection errors
   - Verify traces and metrics are exported to the configured endpoint
   - Test environment variable override works correctly

---

## Benefits

1. **Environment-specific configuration** - Each environment (dev, staging, prod) can have its own OTEL collector endpoint
2. **No hardcoded values** - The default `http://localhost:4317` can be overridden via environment variables
3. **Kubernetes-ready** - Easy to configure per-deployment using environment variables or ConfigMaps
4. **Consistent pattern** - Uses the same configuration approach as database, RabbitMQ, and Redis settings
5. **Fail-safe defaults** - Falls back to `localhost:4317` if environment variable is not set

---

## Summary

| Service | Config.kt | App.kt | application.yaml | K8s Deployment |
|---------|-----------|--------|------------------|----------------|
| Profile | ✓ | ✓ | ✓ | ✓ |
| Basket  | [ ] | [ ] | [ ] | [ ] |
| Menu    | [ ] | [ ] | [ ] | [ ] |
| Order   | [ ] | [ ] | [ ] | [ ] |
| Payment | [ ] | [ ] | [ ] | [ ] |
| WebApp  | ✓ | ✓ | ✓ | ✓ |

**Total changes:** 24 files (18 application files + 6 Kubernetes deployments)

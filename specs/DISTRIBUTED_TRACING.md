# Distributed Tracing Specification

## Overview

This specification defines how to implement distributed tracing across the Foodies microservices architecture using OpenTelemetry. The goal is to provide end-to-end visibility into request flows, including:

- HTTP requests between services (webapp → menu)
- OAuth2 authentication flows (webapp ↔ Keycloak)
- Asynchronous event processing (Keycloak → RabbitMQ → profile)

## Architecture

### Current Service Topology

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           User Request Flow                              │
└─────────────────────────────────────────────────────────────────────────┘

┌──────────┐    HTTP     ┌──────────┐    HTTP     ┌──────────┐
│  Browser │ ──────────► │  webapp  │ ──────────► │   menu   │
│          │             │  :8080   │             │  :8082   │
└──────────┘             └──────────┘             └──────────┘
                               │                       │
                               │ OAuth2                │ PostgreSQL
                               ▼                       ▼
                         ┌──────────┐           ┌──────────────┐
                         │ Keycloak │           │ menu-database│
                         │  :8000   │           └──────────────┘
                         └──────────┘
                               │
                               │ Event (Register/Update/Delete)
                               ▼
                         ┌──────────┐
                         │ RabbitMQ │
                         │  :5672   │
                         └──────────┘
                               │
                               │ Consume
                               ▼
                         ┌──────────┐           ┌─────────────────┐
                         │ profile  │ ────────► │ profile-database│
                         │  :8081   │           └─────────────────┘
                         └──────────┘
```

### Trace Flow Examples

#### 1. Menu Request Flow
```
webapp (HTTP Server Span)
  └── menu-client (HTTP Client Span)
        └── menu (HTTP Server Span)
              └── db.query (Database Span)
```

#### 2. User Registration Flow
```
webapp (HTTP Server Span) - /oauth/callback
  └── keycloak-token-exchange (HTTP Client Span)
        └── keycloak (External - not instrumented)
              └── rabbitmq.publish (Messaging Span) [injected headers]
                    └── profile (Consumer Span) [extracted headers]
                          └── db.insert (Database Span)
```

## Dependencies

### Version Catalog Additions

Add to `gradle/libs.versions.toml`:

```toml
[versions]
# ... existing versions ...
opentelemetry = "1.47.0"
opentelemetry-instrumentation = "2.13.3"
opentelemetry-semconv = "1.29.0-alpha"

[libraries]
# ... existing libraries ...

# OpenTelemetry Core
otel-api = { module = "io.opentelemetry:opentelemetry-api", version.ref = "opentelemetry" }
otel-sdk = { module = "io.opentelemetry:opentelemetry-sdk", version.ref = "opentelemetry" }
otel-sdk-extension-autoconfigure = { module = "io.opentelemetry:opentelemetry-sdk-extension-autoconfigure", version.ref = "opentelemetry" }
otel-semconv = { module = "io.opentelemetry.semconv:opentelemetry-semconv", version.ref = "opentelemetry-semconv" }

# OpenTelemetry Exporters
otel-exporter-otlp = { module = "io.opentelemetry:opentelemetry-exporter-otlp", version.ref = "opentelemetry" }
otel-exporter-logging = { module = "io.opentelemetry:opentelemetry-exporter-logging", version.ref = "opentelemetry" }

# Ktor Instrumentation
otel-ktor-server = { module = "io.opentelemetry.instrumentation:opentelemetry-ktor-3.0", version.ref = "opentelemetry-instrumentation" }
otel-ktor-client = { module = "io.opentelemetry.instrumentation:opentelemetry-ktor-3.0", version.ref = "opentelemetry-instrumentation" }

# Logback MDC Integration
otel-logback-mdc = { module = "io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0", version.ref = "opentelemetry-instrumentation" }
```

### Service Dependencies

**server-shared/build.gradle.kts** (shared tracing utilities):
```kotlin
dependencies {
    api(libs.otel.api)
    api(libs.otel.sdk)
    api(libs.otel.sdk.extension.autoconfigure)
    api(libs.otel.semconv)
    api(libs.otel.exporter.otlp)
    api(libs.otel.exporter.logging)
    api(libs.otel.logback.mdc)
}
```

**webapp/build.gradle.kts**:
```kotlin
dependencies {
    implementation(libs.otel.ktor.server)
    implementation(libs.otel.ktor.client)
}
```

**menu/build.gradle.kts**:
```kotlin
dependencies {
    implementation(libs.otel.ktor.server)
}
```

**profile/build.gradle.kts**:
```kotlin
dependencies {
    implementation(libs.otel.ktor.server)
    // Manual RabbitMQ instrumentation (no official library)
}
```

**keycloak-rabbitmq-publisher/build.gradle.kts**:
```kotlin
dependencies {
    implementation(libs.otel.api)
    // Context propagation for RabbitMQ messages
}
```

## Implementation

### 1. OpenTelemetry SDK Setup

Create `server-shared/src/main/kotlin/io/ktor/foodies/server/telemetry/OpenTelemetrySetup.kt`:

```kotlin
package io.ktor.foodies.server.telemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.sdk.trace.samplers.Sampler
import io.opentelemetry.semconv.ServiceAttributes

data class TelemetryConfig(
    val enabled: Boolean = true,
    val serviceName: String,
    val serviceVersion: String = "1.0.0",
    val otlpEndpoint: String = "http://localhost:4317",
    val samplingRatio: Double = 1.0,
    val exporterType: ExporterType = ExporterType.OTLP
)

enum class ExporterType { OTLP, LOGGING, NOOP }

object OpenTelemetrySetup {

    fun initialize(config: TelemetryConfig): OpenTelemetry {
        if (!config.enabled) {
            return OpenTelemetry.noop()
        }

        val resource = Resource.getDefault().merge(
            Resource.create(
                Attributes.builder()
                    .put(ServiceAttributes.SERVICE_NAME, config.serviceName)
                    .put(ServiceAttributes.SERVICE_VERSION, config.serviceVersion)
                    .put("deployment.environment", System.getenv("ENV") ?: "development")
                    .build()
            )
        )

        val spanExporter: SpanExporter = when (config.exporterType) {
            ExporterType.OTLP -> OtlpGrpcSpanExporter.builder()
                .setEndpoint(config.otlpEndpoint)
                .build()
            ExporterType.LOGGING -> LoggingSpanExporter.create()
            ExporterType.NOOP -> SpanExporter.composite()
        }

        val sampler = if (config.samplingRatio >= 1.0) {
            Sampler.alwaysOn()
        } else {
            Sampler.parentBased(Sampler.traceIdRatioBased(config.samplingRatio))
        }

        val tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .setSampler(sampler)
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .build()

        val propagators = ContextPropagators.create(
            W3CTraceContextPropagator.getInstance()
        )

        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(propagators)
            .buildAndRegisterGlobal()
    }

    fun shutdown(openTelemetry: OpenTelemetry) {
        if (openTelemetry is OpenTelemetrySdk) {
            openTelemetry.sdkTracerProvider.shutdown()
        }
    }
}

fun OpenTelemetry.tracer(name: String): Tracer = getTracer(name)
```

### 2. Ktor Server Instrumentation

Create `server-shared/src/main/kotlin/io/ktor/foodies/server/telemetry/KtorServerTracing.kt`:

```kotlin
package io.ktor.foodies.server.telemetry

import io.ktor.server.application.*
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.ktor.v3_0.server.KtorServerTracing

fun Application.installTracing(openTelemetry: OpenTelemetry) {
    install(KtorServerTracing) {
        setOpenTelemetry(openTelemetry)
    }
}
```

### 3. HTTP Client Instrumentation (webapp → menu)

Update `webapp/src/main/kotlin/io/ktor/foodies/server/menu/MenuService.kt`:

```kotlin
package io.ktor.foodies.server.menu

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.ktor.v3_0.client.KtorClientTracing

interface MenuService {
    suspend fun menuItems(offset: Int, limit: Int): List<MenuItem>
}

class HttpMenuService(
    baseUrl: String,
    private val httpClient: HttpClient
) : MenuService {
    private val menuBaseUrl = baseUrl.trimEnd('/')

    override suspend fun menuItems(offset: Int, limit: Int): List<MenuItem> =
        httpClient.get("$menuBaseUrl/menu") {
            parameter("offset", offset)
            parameter("limit", limit)
        }.body<List<MenuItemResponse>>().map { it.toDomain() }
}

// Factory function for instrumented HTTP client
fun createInstrumentedHttpClient(
    openTelemetry: OpenTelemetry,
    configure: io.ktor.client.HttpClientConfig<*>.() -> Unit = {}
): HttpClient = HttpClient {
    install(KtorClientTracing) {
        setOpenTelemetry(openTelemetry)
    }
    configure()
}
```

### 4. RabbitMQ Context Propagation

#### 4.1 Publisher Side (Keycloak SPI)

Update `keycloak-rabbitmq-publisher/src/main/kotlin/io/ktor/foodies/keycloak/ProfileWebhookEventListener.kt`:

```kotlin
package io.ktor.foodies.keycloak

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Connection
import io.ktor.foodies.user.event.UserEvent
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapSetter
import kotlinx.serialization.json.Json
import org.jboss.logging.Logger
import org.keycloak.events.Event
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.admin.AdminEvent

internal class ProfileWebhookEventListener(
    private val rabbitConfig: RabbitConfig,
    private val connection: Lazy<Connection>,
) : EventListenerProvider {
    private val logger = Logger.getLogger(ProfileWebhookEventListener::class.java)
    private val tracer = GlobalOpenTelemetry.getTracer("keycloak-event-publisher")

    private val channel by lazy {
        connection.value.createChannel().apply {
            queueDeclare(rabbitConfig.queue, true, false, false, null)
        }
    }

    override fun onEvent(event: AdminEvent?, includeRepresentation: Boolean) = Unit

    override fun onEvent(event: Event?) {
        val userEvent = event?.toUserEvent() ?: return

        // Create a span for the publish operation
        val span = tracer.spanBuilder("rabbitmq.publish")
            .setSpanKind(SpanKind.PRODUCER)
            .setAttribute("messaging.system", "rabbitmq")
            .setAttribute("messaging.destination.name", rabbitConfig.queue)
            .setAttribute("messaging.operation", "publish")
            .setAttribute("user.subject", userEvent.subject)
            .setAttribute("event.type", userEvent::class.simpleName ?: "unknown")
            .startSpan()

        try {
            span.makeCurrent().use {
                val message = Json.encodeToString(UserEvent.serializer(), userEvent)

                // Inject trace context into message headers
                val headers = mutableMapOf<String, Any>()
                GlobalOpenTelemetry.getPropagators().textMapPropagator.inject(
                    Context.current(),
                    headers,
                    TextMapSetter { carrier, key, value ->
                        carrier?.put(key, value)
                    }
                )

                val properties = AMQP.BasicProperties.Builder()
                    .headers(headers)
                    .contentType("application/json")
                    .deliveryMode(2) // persistent
                    .build()

                channel.basicPublish("", rabbitConfig.queue, properties, message.toByteArray())
                span.setStatus(StatusCode.OK)
            }
        } catch (e: Exception) {
            span.setStatus(StatusCode.ERROR, e.message ?: "Unknown error")
            span.recordException(e)
            logger.error(
                "Failed to forward registration event to profile queue ${rabbitConfig.queue} for userId=${userEvent.subject}",
                e
            )
        } finally {
            span.end()
        }
    }

    override fun close() {
        runCatching { if (channel.isOpen) channel.close() }.onFailure {
            logger.warn("Failed to close RabbitMQ channel", it)
        }
    }
}
```

#### 4.2 Consumer Side (Profile Service)

Update `profile/src/main/kotlin/io/ktor/foodies/server/consumers/Consumer.kt`:

```kotlin
package io.ktor.foodies.server.consumers

import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Channel
import com.rabbitmq.client.DeliverCallback
import com.rabbitmq.client.Delivery
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(Consumer::class.java)

fun interface Consumer {
    fun process(): Flow<Unit>
}

class Message<A>(
    val value: A,
    val traceContext: Context,
    private val delivery: Delivery,
    private val channel: Channel
) {
    fun ack() = channel.basicAck(delivery.envelope.deliveryTag, false)
    fun nack() = channel.basicNack(delivery.envelope.deliveryTag, false, false)
}

private val headerGetter = object : TextMapGetter<Map<String, Any?>> {
    override fun keys(carrier: Map<String, Any?>): Iterable<String> = carrier.keys
    override fun get(carrier: Map<String, Any?>?, key: String): String? {
        return carrier?.get(key)?.toString()
    }
}

inline fun <reified A> Channel.messages(queueName: String): Flow<Message<A>> =
    messages(serializer(), queueName)

fun <A> Channel.messages(serializer: KSerializer<A>, queueName: String): Flow<Message<A>> = channelFlow {
    val tracer = GlobalOpenTelemetry.getTracer("rabbitmq-consumer")
    val propagators = GlobalOpenTelemetry.getPropagators()

    val deliverCallback = DeliverCallback { _, delivery ->
        // Extract trace context from message headers
        val headers = delivery.properties?.headers?.mapValues { it.value }
            ?: emptyMap()

        val extractedContext = propagators.textMapPropagator.extract(
            Context.current(),
            headers,
            headerGetter
        )

        // Create consumer span as child of the extracted context
        val span = tracer.spanBuilder("rabbitmq.consume")
            .setParent(extractedContext)
            .setSpanKind(SpanKind.CONSUMER)
            .setAttribute("messaging.system", "rabbitmq")
            .setAttribute("messaging.destination.name", queueName)
            .setAttribute("messaging.operation", "receive")
            .setAttribute("messaging.message.id", delivery.envelope.deliveryTag.toString())
            .startSpan()

        val messageContext = extractedContext.with(span)

        runCatching {
            Json.decodeFromString(serializer, delivery.body.decodeToString())
        }.fold(
            { payload ->
                trySendBlocking(Message(payload, messageContext, delivery, this@messages))
            },
            { error ->
                span.recordException(error)
                span.end()
                close(error)
            }
        )
    }

    val cancelCallback = CancelCallback {
        logger.warn("Registration consumer cancelled for queue $queueName")
        channel.close()
    }

    val consumerTag = basicConsume(queueName, false, deliverCallback, cancelCallback)
    awaitClose { basicCancel(consumerTag) }
}
```

#### 4.3 Event Processing with Tracing

Update `profile/src/main/kotlin/io/ktor/foodies/server/consumers/UserEventConsumer.kt`:

```kotlin
package io.ktor.foodies.server.consumers

import io.ktor.foodies.server.profile.ProfileRepository
import io.ktor.foodies.user.event.UserEvent
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("UserEventConsumer")

fun userEventConsumer(
    userEvents: Flow<Message<UserEvent>>,
    profileRepository: ProfileRepository
) = Consumer {
    val tracer = GlobalOpenTelemetry.getTracer("profile-service")

    userEvents.map { message ->
        val parentContext = message.traceContext

        // Create processing span as child of consumer span
        val span = tracer.spanBuilder("process.user_event")
            .setParent(parentContext)
            .setAttribute("event.type", message.value::class.simpleName ?: "unknown")
            .startSpan()

        span.makeCurrent().use {
            try {
                when (val event = message.value) {
                    is UserEvent.Registration -> {
                        span.setAttribute("user.subject", event.subject)
                        profileRepository.insertOrIgnore(
                            event.subject,
                            event.email,
                            event.firstName,
                            event.lastName
                        )
                        logger.info("Processed registration for subject ${event.subject}")
                    }
                    is UserEvent.UpdateProfile -> {
                        span.setAttribute("user.subject", event.subject)
                        profileRepository.upsert(
                            event.subject,
                            event.email,
                            event.firstName,
                            event.lastName
                        )
                        logger.info("Processed update for subject ${event.subject}")
                    }
                    is UserEvent.Delete -> {
                        span.setAttribute("user.subject", event.subject)
                        profileRepository.deleteBySubject(event.subject)
                        logger.info("Processed delete for subject ${event.subject}")
                    }
                }
                message.ack()
                span.setStatus(StatusCode.OK)
            } catch (e: Exception) {
                span.recordException(e)
                span.setStatus(StatusCode.ERROR, e.message ?: "Processing failed")
                message.nack()
                throw e
            } finally {
                span.end()
            }
        }
    }.retry { e ->
        delay(1000)
        logger.error("Failed to process user event, retrying", e)
        true
    }
}
```

### 5. Database Tracing

Create `server-shared/src/main/kotlin/io/ktor/foodies/server/telemetry/DatabaseTracing.kt`:

```kotlin
package io.ktor.foodies.server.telemetry

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import org.jetbrains.exposed.sql.Transaction

inline fun <T> Transaction.traced(
    operationName: String,
    tableName: String,
    crossinline block: Transaction.() -> T
): T {
    val tracer = GlobalOpenTelemetry.getTracer("database")
    val span = tracer.spanBuilder("db.$operationName")
        .setSpanKind(SpanKind.CLIENT)
        .setAttribute("db.system", "postgresql")
        .setAttribute("db.operation", operationName)
        .setAttribute("db.sql.table", tableName)
        .startSpan()

    return span.makeCurrent().use {
        try {
            val result = block()
            span.setStatus(StatusCode.OK)
            result
        } catch (e: Exception) {
            span.recordException(e)
            span.setStatus(StatusCode.ERROR, e.message ?: "Database operation failed")
            throw e
        } finally {
            span.end()
        }
    }
}
```

### 6. Logging Integration

Update `logback.xml` in each service to include trace context:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>
                %d{ISO8601} [%thread] %-5level %logger{36} - [traceId=%X{trace_id} spanId=%X{span_id}] %msg%n
            </pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

## Configuration

### Environment Variables

Each service should support these environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `OTEL_ENABLED` | Enable/disable tracing | `true` |
| `OTEL_SERVICE_NAME` | Service name for traces | Service-specific |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OTLP collector endpoint | `http://localhost:4317` |
| `OTEL_TRACES_SAMPLER` | Sampling strategy | `always_on` |
| `OTEL_TRACES_SAMPLER_ARG` | Sampling ratio (0.0-1.0) | `1.0` |

### Application YAML Configuration

Add to each service's `application.yaml`:

```yaml
config:
  # ... existing config ...
  telemetry:
    enabled: "$OTEL_ENABLED:true"
    serviceName: "$OTEL_SERVICE_NAME:webapp"  # or menu, profile
    otlpEndpoint: "$OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317"
    samplingRatio: "$OTEL_TRACES_SAMPLER_ARG:1.0"
    exporter: "$OTEL_EXPORTER:otlp"  # otlp, logging, noop
```

## Infrastructure

### Docker Compose (Development)

Add to `webapp/docker-compose.yaml`:

```yaml
services:
  # ... existing services ...

  jaeger:
    image: jaegertracing/all-in-one:1.54
    ports:
      - "16686:16686"  # Jaeger UI
      - "4317:4317"    # OTLP gRPC
      - "4318:4318"    # OTLP HTTP
    environment:
      COLLECTOR_OTLP_ENABLED: true

  # Alternative: OpenTelemetry Collector
  otel-collector:
    image: otel/opentelemetry-collector-contrib:0.96.0
    command: ["--config=/etc/otel-collector-config.yaml"]
    volumes:
      - ./otel-collector-config.yaml:/etc/otel-collector-config.yaml
    ports:
      - "4317:4317"    # OTLP gRPC
      - "4318:4318"    # OTLP HTTP
      - "8888:8888"    # Prometheus metrics
```

### OpenTelemetry Collector Configuration

Create `otel-collector-config.yaml`:

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  batch:
    timeout: 10s
    send_batch_size: 1024

  memory_limiter:
    check_interval: 1s
    limit_mib: 512

  resource:
    attributes:
      - key: deployment.environment
        value: development
        action: upsert

exporters:
  jaeger:
    endpoint: jaeger:14250
    tls:
      insecure: true

  logging:
    verbosity: detailed

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, batch, resource]
      exporters: [jaeger, logging]
```

### Kubernetes Deployment

Add to `k8s/infrastructure/`:

**jaeger.yaml**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: jaeger
  namespace: foodies
spec:
  replicas: 1
  selector:
    matchLabels:
      app: jaeger
  template:
    metadata:
      labels:
        app: jaeger
    spec:
      containers:
      - name: jaeger
        image: jaegertracing/all-in-one:1.54
        ports:
        - containerPort: 16686
          name: ui
        - containerPort: 4317
          name: otlp-grpc
        - containerPort: 4318
          name: otlp-http
        env:
        - name: COLLECTOR_OTLP_ENABLED
          value: "true"
---
apiVersion: v1
kind: Service
metadata:
  name: jaeger
  namespace: foodies
spec:
  selector:
    app: jaeger
  ports:
  - name: ui
    port: 16686
    targetPort: 16686
  - name: otlp-grpc
    port: 4317
    targetPort: 4317
  - name: otlp-http
    port: 4318
    targetPort: 4318
```

Update service deployments to include telemetry configuration:

```yaml
env:
  - name: OTEL_ENABLED
    value: "true"
  - name: OTEL_SERVICE_NAME
    value: "webapp"  # or menu, profile
  - name: OTEL_EXPORTER_OTLP_ENDPOINT
    value: "http://jaeger:4317"
```

## Trace Attributes

### Standard Attributes (Semantic Conventions)

| Attribute | Description | Example |
|-----------|-------------|---------|
| `service.name` | Service identifier | `webapp`, `menu`, `profile` |
| `service.version` | Service version | `1.0.0` |
| `http.method` | HTTP method | `GET`, `POST` |
| `http.url` | Full request URL | `http://menu:8082/menu` |
| `http.status_code` | Response status code | `200`, `404` |
| `http.route` | URL route pattern | `/menu/{id}` |
| `db.system` | Database type | `postgresql` |
| `db.operation` | Operation type | `SELECT`, `INSERT` |
| `db.sql.table` | Table name | `menu_items`, `profiles` |
| `messaging.system` | Messaging system | `rabbitmq` |
| `messaging.destination.name` | Queue name | `profile.registration` |
| `messaging.operation` | Operation type | `publish`, `receive` |

### Custom Attributes

| Attribute | Description | Service |
|-----------|-------------|---------|
| `user.subject` | Keycloak user subject | profile |
| `event.type` | User event type | profile |
| `menu.item.id` | Menu item ID | menu |

## Testing

### Verifying Trace Propagation

1. **Start infrastructure**:
   ```bash
   docker compose up jaeger rabbitmq keycloak
   ```

2. **Start services with tracing**:
   ```bash
   OTEL_ENABLED=true OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317 ./gradlew :webapp:run
   OTEL_ENABLED=true OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317 ./gradlew :menu:run
   OTEL_ENABLED=true OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317 ./gradlew :profile:run
   ```

3. **Generate traces**:
   - Browse to `http://localhost:8080` and navigate menu
   - Register a new user through Keycloak

4. **View traces**:
   - Open Jaeger UI at `http://localhost:16686`
   - Select service and search for traces

### Unit Tests

```kotlin
class TracingTest {
    @Test
    fun `trace context is propagated through RabbitMQ`() {
        // Test that traceparent header is injected on publish
        // and extracted on consume
    }

    @Test
    fun `HTTP client propagates trace context`() {
        // Test that W3C trace context headers are added
        // to outgoing HTTP requests
    }
}
```

## Implementation Checklist

### Phase 1: Core Setup
- [ ] Add OpenTelemetry dependencies to version catalog
- [ ] Create `OpenTelemetrySetup` in server-shared
- [ ] Create telemetry configuration classes
- [ ] Update logback configuration for trace context

### Phase 2: HTTP Tracing
- [ ] Add Ktor server tracing to webapp
- [ ] Add Ktor server tracing to menu
- [ ] Add Ktor server tracing to profile
- [ ] Add Ktor client tracing to webapp (menu calls)

### Phase 3: RabbitMQ Tracing
- [ ] Update Keycloak SPI to inject trace context
- [ ] Update Consumer to extract trace context
- [ ] Update UserEventConsumer to use trace context

### Phase 4: Database Tracing
- [ ] Add database tracing utilities
- [ ] Instrument repository operations (optional)

### Phase 5: Infrastructure
- [ ] Add Jaeger to docker-compose
- [ ] Add Jaeger to Kubernetes manifests
- [ ] Update service deployments with env vars

### Phase 6: Testing & Validation
- [ ] Verify end-to-end trace propagation
- [ ] Verify trace visibility in Jaeger
- [ ] Document runbook for trace analysis

## References

- [OpenTelemetry Specification](https://opentelemetry.io/docs/specs/otel/)
- [OpenTelemetry Kotlin](https://opentelemetry.io/docs/languages/java/)
- [Ktor OpenTelemetry Instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/ktor)
- [W3C Trace Context](https://www.w3.org/TR/trace-context/)
- [Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/)
- [Jaeger Documentation](https://www.jaegertracing.io/docs/)

# Monitoring & Observability Specification

## Overview

This specification defines the comprehensive monitoring and observability strategy for the Foodies microservices platform. It builds upon the existing health check infrastructure ([HEALTH_CHECK_SPECIFICATION.md](fleet-file://mmglq7uf96d8i197ro8d/Users/simonvergauwen/Developer/foodies/specs/HEALTH_CHECK_SPECIFICATION.md?type=file&root=%252F)) and distributed tracing design ([DISTRIBUTED_TRACING.md](fleet-file://mmglq7uf96d8i197ro8d/Users/simonvergauwen/Developer/foodies/specs/DISTRIBUTED_TRACING.md?type=file&root=%252F)) to provide complete observability across the system.

## Current Observability State

### ✅ Implemented

| Component | Status | Details |
|-----------|--------|---------|
| **Health Checks** | Complete | Cohort library, 3-probe strategy (startup/liveness/readiness) |
| **Kubernetes Probes** | Complete | All services configured with proper probe timings |
| **Logging** | Basic | Logback with console output, structured format |
| **Infrastructure Health** | Complete | PostgreSQL, Redis, RabbitMQ probes configured |
| **Service Dependencies** | Complete | HTTP endpoint checks in readiness probes |

### 🔶 Partially Implemented

| Component | Status | Details |
|-----------|--------|---------|
| **Metrics** | Available | Micrometer dependency present but not integrated |
| **Custom Health Checks** | Partial | RabbitMQ custom check implemented, others using Cohort built-ins |

### 🔴 Not Implemented

| Component | Status | Details |
|-----------|--------|---------|
| **Distributed Tracing** | Designed | Full OpenTelemetry specification available but not implemented |
| **Metrics Export** | Not Started | Prometheus integration needed |
| **Centralized Logging** | Not Started | Log aggregation infrastructure needed |
| **Dashboards** | Not Started | Grafana dashboards not created |
| **Alerting** | Not Started | Alert rules not defined |

---

## Architecture

### Observability Stack

```
┌──────────────────────────────────────────────────────────────────────┐
│                         Observability Stack                          │
└──────────────────────────────────────────────────────────────────────┘

                    ┌─────────────────────┐
                    │   Grafana UI        │
                    │   Dashboards        │
                    │   Alerts            │
                    └─────────────────────┘
                             │
           ┌─────────────────┼─────────────────┐
           │                 │                 │
    ┌──────▼──────┐   ┌─────▼─────┐   ┌──────▼──────┐
    │ Prometheus  │   │   Jaeger   │   │     Loki    │
    │  (Metrics)  │   │  (Traces)  │   │    (Logs)   │
    └─────────────┘   └────────────┘   └─────────────┘
           ▲                 ▲                 ▲
           │                 │                 │
    ┌──────┴──────┬──────────┴──────┬──────────┴──────┐
    │             │                  │                  │
┌───▼────┐  ┌────▼────┐  ┌─────────▼──┐  ┌───────────▼───┐
│ webapp │  │  menu   │  │   basket   │  │    profile    │
│ :8080  │  │  :8082  │  │   :8083    │  │     :8081     │
└────────┘  └─────────┘  └────────────┘  └───────────────┘
    │            │              │                 │
    └────────────┴──────────────┴─────────────────┘
                        │
                 ┌──────▼──────┐
                 │ Infrastructure│
                 │ PostgreSQL   │
                 │ Redis        │
                 │ RabbitMQ     │
                 │ Keycloak     │
                 └─────────────┘
```

### Three Pillars of Observability

1. **Metrics** - Quantitative measurements over time (CPU, memory, request rates)
2. **Logs** - Discrete event records with context
3. **Traces** - Request flow visualization across services

---

## Metrics Implementation

### Dependencies

**Add to `gradle/libs.versions.toml`:**

```toml
[versions]
micrometer = "1.16.1"
micrometer-registry-prometheus = "1.16.1"

[libraries]
micrometer-core = { module = "io.micrometer:micrometer-core", version.ref = "micrometer" }
micrometer-registry-prometheus = { module = "io.micrometer:micrometer-registry-prometheus", version.ref = "micrometer-registry-prometheus" }
```

**Update `server-shared/build.gradle.kts`:**

```kotlin
dependencies {
    // Existing dependencies...

    // Metrics
    api(libs.micrometer.core)
    api(libs.micrometer.registry.prometheus)
}
```

### Metrics Configuration

**Create `server-shared/src/main/kotlin/io/ktor/foodies/server/metrics/MetricsConfig.kt`:**

```kotlin
package io.ktor.foodies.server.metrics

import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.binder.jvm.*
import io.micrometer.core.instrument.binder.system.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.exporter.common.TextFormat

data class MetricsConfig(
    val enabled: Boolean = true,
    val serviceName: String,
    val includeJvmMetrics: Boolean = true,
    val includeSystemMetrics: Boolean = true
)

fun Application.installMetrics(config: MetricsConfig): PrometheusMeterRegistry {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT, Clock.SYSTEM)

    // Install Ktor metrics plugin
    install(MicrometerMetrics) {
        this.registry = registry

        // Tag all metrics with service name
        meterBinders = buildList {
            if (config.includeJvmMetrics) {
                add(JvmMemoryMetrics())
                add(JvmGcMetrics())
                add(JvmThreadMetrics())
                add(ClassLoaderMetrics())
            }
            if (config.includeSystemMetrics) {
                add(ProcessorMetrics())
                add(UptimeMetrics())
                add(FileDescriptorMetrics())
            }
        }

        // Add common tags
        distributionStatisticConfig = distributionStatisticConfig.copy(
            percentiles = doubleArrayOf(0.5, 0.95, 0.99)
        )
    }

    // Expose metrics endpoint
    routing {
        get("/metrics") {
            call.respondText(
                registry.scrape(),
                contentType = io.ktor.http.ContentType.parse(TextFormat.CONTENT_TYPE_004)
            )
        }
    }

    return registry
}
```

### Service-Specific Metrics

#### WebApp Service

**Update `webapp/src/main/kotlin/io/ktor/foodies/server/WebApp.kt`:**

```kotlin
fun Application.app(config: Config, httpClient: HttpClient) {
    val registry = installMetrics(MetricsConfig(
        serviceName = "webapp",
        includeJvmMetrics = true,
        includeSystemMetrics = true
    ))

    // Custom metrics
    val menuRequestsCounter = registry.counter("foodies.menu.requests", "service", "webapp")
    val basketRequestsCounter = registry.counter("foodies.basket.requests", "service", "webapp")

    // Rest of configuration...
}
```

#### Menu Service

**Update `menu/src/main/kotlin/io/ktor/foodies/menu/MenuApp.kt`:**

```kotlin
fun Application.app(module: MenuModule) {
    val registry = installMetrics(MetricsConfig(
        serviceName = "menu",
        includeJvmMetrics = true,
        includeSystemMetrics = true
    ))

    // Custom metrics
    val menuItemsCreatedCounter = registry.counter("foodies.menu.items.created")
    val menuItemsDeletedCounter = registry.counter("foodies.menu.items.deleted")
    val menuItemsFetchedTimer = registry.timer("foodies.menu.items.fetch.duration")

    // Rest of configuration...
}
```

#### Basket Service

**Update `basket/src/main/kotlin/io/ktor/foodies/basket/BasketApp.kt`:**

```kotlin
fun Application.app(module: BasketModule) {
    val registry = installMetrics(MetricsConfig(
        serviceName = "basket",
        includeJvmMetrics = true,
        includeSystemMetrics = true
    ))

    // Custom metrics
    val basketsCreatedCounter = registry.counter("foodies.basket.created")
    val basketItemsAddedCounter = registry.counter("foodies.basket.items.added")
    val basketItemsRemovedCounter = registry.counter("foodies.basket.items.removed")
    val redisOperationTimer = registry.timer("foodies.basket.redis.duration")

    // Rest of configuration...
}
```

#### Profile Service

**Update `profile/src/main/kotlin/io/ktor/foodies/server/ProfileApp.kt`:**

```kotlin
fun Application.app(module: ProfileModule) {
    val registry = installMetrics(MetricsConfig(
        serviceName = "profile",
        includeJvmMetrics = true,
        includeSystemMetrics = true
    ))

    // Custom metrics
    val eventsProcessedCounter = registry.counter("foodies.profile.events.processed")
    val eventsFailedCounter = registry.counter("foodies.profile.events.failed")
    val eventProcessingTimer = registry.timer("foodies.profile.events.duration")
    val rabbitmqConnectionGauge = registry.gauge("foodies.profile.rabbitmq.connected",
        emptyList(), module.connection) { if (it.isOpen) 1.0 else 0.0 }

    // Rest of configuration...
}
```

### Standard Metrics Exposed

#### HTTP Server Metrics (Automatic via MicrometerMetrics plugin)

- `http.server.requests.count` - Total HTTP requests
- `http.server.requests.duration` - Request duration histogram
- Tags: `method`, `status`, `uri`, `exception`

#### JVM Metrics

- `jvm.memory.used` - Memory usage by type (heap, non-heap)
- `jvm.memory.committed` - Committed memory
- `jvm.gc.pause` - GC pause duration
- `jvm.threads.live` - Live thread count
- `jvm.threads.daemon` - Daemon thread count
- `jvm.classes.loaded` - Loaded classes

#### System Metrics

- `system.cpu.usage` - CPU usage percentage
- `process.uptime` - Process uptime in seconds
- `process.files.open` - Open file descriptors

#### Custom Business Metrics

**Menu Service:**
- `foodies.menu.items.created` - Counter
- `foodies.menu.items.deleted` - Counter
- `foodies.menu.items.fetch.duration` - Timer

**Basket Service:**
- `foodies.basket.created` - Counter
- `foodies.basket.items.added` - Counter
- `foodies.basket.items.removed` - Counter
- `foodies.basket.redis.duration` - Timer

**Profile Service:**
- `foodies.profile.events.processed` - Counter (tags: `event_type`)
- `foodies.profile.events.failed` - Counter (tags: `event_type`, `error_type`)
- `foodies.profile.events.duration` - Timer (tags: `event_type`)
- `foodies.profile.rabbitmq.connected` - Gauge

**WebApp Service:**
- `foodies.menu.requests` - Counter
- `foodies.basket.requests` - Counter
- `foodies.auth.login.success` - Counter
- `foodies.auth.login.failure` - Counter

---

## Prometheus Integration

### Prometheus Deployment

**Create `k8s/infrastructure/prometheus.yaml`:**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
  namespace: foodies
data:
  prometheus.yml: |
    global:
      scrape_interval: 15s
      evaluation_interval: 15s
      external_labels:
        cluster: 'foodies-cluster'
        environment: 'development'

    scrape_configs:
      - job_name: 'webapp'
        kubernetes_sd_configs:
          - role: pod
            namespaces:
              names:
                - foodies
        relabel_configs:
          - source_labels: [__meta_kubernetes_pod_label_app]
            action: keep
            regex: webapp
          - source_labels: [__meta_kubernetes_pod_ip]
            action: replace
            target_label: __address__
            replacement: $1:8080
          - source_labels: [__meta_kubernetes_pod_name]
            action: replace
            target_label: pod

      - job_name: 'menu'
        kubernetes_sd_configs:
          - role: pod
            namespaces:
              names:
                - foodies
        relabel_configs:
          - source_labels: [__meta_kubernetes_pod_label_app]
            action: keep
            regex: menu
          - source_labels: [__meta_kubernetes_pod_ip]
            action: replace
            target_label: __address__
            replacement: $1:8082
          - source_labels: [__meta_kubernetes_pod_name]
            action: replace
            target_label: pod

      - job_name: 'basket'
        kubernetes_sd_configs:
          - role: pod
            namespaces:
              names:
                - foodies
        relabel_configs:
          - source_labels: [__meta_kubernetes_pod_label_app]
            action: keep
            regex: basket
          - source_labels: [__meta_kubernetes_pod_ip]
            action: replace
            target_label: __address__
            replacement: $1:8083
          - source_labels: [__meta_kubernetes_pod_name]
            action: replace
            target_label: pod

      - job_name: 'profile'
        kubernetes_sd_configs:
          - role: pod
            namespaces:
              names:
                - foodies
        relabel_configs:
          - source_labels: [__meta_kubernetes_pod_label_app]
            action: keep
            regex: profile
          - source_labels: [__meta_kubernetes_pod_ip]
            action: replace
            target_label: __address__
            replacement: $1:8081
          - source_labels: [__meta_kubernetes_pod_name]
            action: replace
            target_label: pod

      # Infrastructure monitoring
      - job_name: 'postgres'
        static_configs:
          - targets:
              - 'menu-postgres:5432'
              - 'profile-postgres:5432'

      - job_name: 'redis'
        static_configs:
          - targets: ['redis:6379']

      - job_name: 'rabbitmq'
        static_configs:
          - targets: ['rabbitmq:15692']

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: prometheus
  namespace: foodies
spec:
  replicas: 1
  selector:
    matchLabels:
      app: prometheus
  template:
    metadata:
      labels:
        app: prometheus
    spec:
      serviceAccountName: prometheus
      containers:
        - name: prometheus
          image: prom/prometheus:v2.49.0
          args:
            - '--config.file=/etc/prometheus/prometheus.yml'
            - '--storage.tsdb.path=/prometheus'
            - '--storage.tsdb.retention.time=7d'
            - '--web.enable-lifecycle'
          ports:
            - containerPort: 9090
              name: http
          volumeMounts:
            - name: config
              mountPath: /etc/prometheus
            - name: storage
              mountPath: /prometheus
          resources:
            requests:
              memory: "256Mi"
              cpu: "100m"
            limits:
              memory: "1Gi"
              cpu: "500m"
      volumes:
        - name: config
          configMap:
            name: prometheus-config
        - name: storage
          emptyDir: {}

---
apiVersion: v1
kind: Service
metadata:
  name: prometheus
  namespace: foodies
spec:
  selector:
    app: prometheus
  ports:
    - name: http
      port: 9090
      targetPort: 9090

---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: prometheus
  namespace: foodies

---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: prometheus
rules:
  - apiGroups: [""]
    resources:
      - nodes
      - nodes/proxy
      - services
      - endpoints
      - pods
    verbs: ["get", "list", "watch"]
  - apiGroups: ["extensions"]
    resources:
      - ingresses
    verbs: ["get", "list", "watch"]

---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: prometheus
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: prometheus
subjects:
  - kind: ServiceAccount
    name: prometheus
    namespace: foodies
```

### Docker Compose (Development)

**Add to `webapp/docker-compose.yaml`:**

```yaml
services:
  # ... existing services ...

  prometheus:
    image: prom/prometheus:v2.49.0
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--storage.tsdb.retention.time=7d'

volumes:
  prometheus-data:
```

**Create `webapp/prometheus.yml`:**

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'webapp'
    static_configs:
      - targets: ['host.docker.internal:8080']

  - job_name: 'menu'
    static_configs:
      - targets: ['host.docker.internal:8082']

  - job_name: 'basket'
    static_configs:
      - targets: ['host.docker.internal:8083']

  - job_name: 'profile'
    static_configs:
      - targets: ['host.docker.internal:8081']
```

---

## Grafana Dashboards

### Grafana Deployment

**Create `k8s/infrastructure/grafana.yaml`:**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-datasources
  namespace: foodies
data:
  datasources.yaml: |
    apiVersion: 1
    datasources:
      - name: Prometheus
        type: prometheus
        access: proxy
        url: http://prometheus:9090
        isDefault: true
        editable: true

      - name: Jaeger
        type: jaeger
        access: proxy
        url: http://jaeger:16686
        editable: true

      - name: Loki
        type: loki
        access: proxy
        url: http://loki:3100
        editable: true

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: grafana
  namespace: foodies
spec:
  replicas: 1
  selector:
    matchLabels:
      app: grafana
  template:
    metadata:
      labels:
        app: grafana
    spec:
      containers:
        - name: grafana
          image: grafana/grafana:10.2.3
          ports:
            - containerPort: 3000
              name: http
          env:
            - name: GF_AUTH_ANONYMOUS_ENABLED
              value: "true"
            - name: GF_AUTH_ANONYMOUS_ORG_ROLE
              value: "Admin"
            - name: GF_SECURITY_ADMIN_PASSWORD
              value: "admin"
          volumeMounts:
            - name: datasources
              mountPath: /etc/grafana/provisioning/datasources
          resources:
            requests:
              memory: "128Mi"
              cpu: "100m"
            limits:
              memory: "256Mi"
              cpu: "500m"
      volumes:
        - name: datasources
          configMap:
            name: grafana-datasources

---
apiVersion: v1
kind: Service
metadata:
  name: grafana
  namespace: foodies
spec:
  selector:
    app: grafana
  ports:
    - name: http
      port: 3000
      targetPort: 3000
```

### Dashboard Templates

#### 1. Service Overview Dashboard

**Metrics:**
- Request rate (requests/second)
- Error rate (errors/second, %)
- Latency percentiles (p50, p95, p99)
- Active connections
- JVM memory usage
- CPU usage

**Panels:**

```
┌────────────────────────────────────────────────────────────┐
│                    Service: Menu                           │
├────────────────────────────────────────────────────────────┤
│  Request Rate    │  Error Rate    │  Latency (p95)         │
│  125 req/s       │  0.5%          │  45ms                  │
├────────────────────────────────────────────────────────────┤
│                 Request Rate Over Time                     │
│  [Graph showing requests/second over last hour]            │
├────────────────────────────────────────────────────────────┤
│  Latency Percentiles      │  JVM Memory                    │
│  [p50/p95/p99 graph]      │  [Heap/Non-heap usage]         │
├────────────────────────────────────────────────────────────┤
│  Active Threads           │  GC Pauses                     │
│  [Thread count graph]     │  [GC duration graph]           │
└────────────────────────────────────────────────────────────┘
```

#### 2. Infrastructure Dashboard

**Metrics:**
- Database connection pool utilization
- Redis memory usage
- RabbitMQ queue depth
- RabbitMQ message rate

**Panels:**

```
┌────────────────────────────────────────────────────────────┐
│                    Infrastructure Health                   │
├────────────────────────────────────────────────────────────┤
│  PostgreSQL Connections   │  Redis Memory Usage            │
│  [Active/Idle graph]      │  [Memory usage graph]          │
├────────────────────────────────────────────────────────────┤
│  RabbitMQ Queue Depth     │  RabbitMQ Message Rate         │
│  [Queue depth graph]      │  [Messages/sec graph]          │
└────────────────────────────────────────────────────────────┘
```

#### 3. Business Metrics Dashboard

**Metrics:**
- Menu items created/deleted
- Basket operations (add/remove)
- User registrations
- Authentication success/failure rate

**Panels:**

```
┌────────────────────────────────────────────────────────────┐
│                    Business Metrics                        │
├────────────────────────────────────────────────────────────┤
│  User Registrations       │  Menu Item Operations          │
│  [Count over time]        │  [Created/Deleted graph]       │
├────────────────────────────────────────────────────────────┤
│  Basket Operations        │  Authentication                │
│  [Add/Remove graph]       │  [Success/Failure graph]       │
└────────────────────────────────────────────────────────────┘
```

---

## Logging Strategy

### Structured Logging

**Update logback configuration in each service:**

**File:** `{service}/src/main/resources/logback.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Console appender with JSON formatting -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>trace_id</includeMdcKeyName>
            <includeMdcKeyName>span_id</includeMdcKeyName>
            <customFields>{"service":"${SERVICE_NAME:-unknown}"}</customFields>
            <fieldNames>
                <timestamp>@timestamp</timestamp>
                <version>[ignore]</version>
            </fieldNames>
        </encoder>
    </appender>

    <!-- Fallback pattern appender for development -->
    <appender name="CONSOLE_PATTERN" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{ISO8601} [%thread] %-5level %logger{36} - [traceId=%X{trace_id} spanId=%X{span_id}] %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Use JSON in production, pattern in development -->
    <if condition='property("LOG_FORMAT").equals("json")'>
        <then>
            <root level="INFO">
                <appender-ref ref="CONSOLE"/>
            </root>
        </then>
        <else>
            <root level="INFO">
                <appender-ref ref="CONSOLE_PATTERN"/>
            </root>
        </else>
    </if>
</configuration>
```

**Dependencies:**

```toml
[libraries]
logstash-logback-encoder = { module = "net.logstash.logback:logstash-logback-encoder", version = "7.4" }
```

### Log Aggregation with Loki

**Create `k8s/infrastructure/loki.yaml`:**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: loki-config
  namespace: foodies
data:
  loki.yaml: |
    auth_enabled: false

    server:
      http_listen_port: 3100

    ingester:
      lifecycler:
        address: 127.0.0.1
        ring:
          kvstore:
            store: inmemory
          replication_factor: 1
      chunk_idle_period: 5m
      chunk_retain_period: 30s

    schema_config:
      configs:
        - from: 2024-01-01
          store: boltdb-shipper
          object_store: filesystem
          schema: v11
          index:
            prefix: index_
            period: 24h

    storage_config:
      boltdb_shipper:
        active_index_directory: /loki/index
        cache_location: /loki/cache
        shared_store: filesystem
      filesystem:
        directory: /loki/chunks

    limits_config:
      enforce_metric_name: false
      reject_old_samples: true
      reject_old_samples_max_age: 168h

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: loki
  namespace: foodies
spec:
  replicas: 1
  selector:
    matchLabels:
      app: loki
  template:
    metadata:
      labels:
        app: loki
    spec:
      containers:
        - name: loki
          image: grafana/loki:2.9.3
          args:
            - -config.file=/etc/loki/loki.yaml
          ports:
            - containerPort: 3100
              name: http
          volumeMounts:
            - name: config
              mountPath: /etc/loki
            - name: storage
              mountPath: /loki
          resources:
            requests:
              memory: "256Mi"
              cpu: "100m"
            limits:
              memory: "512Mi"
              cpu: "500m"
      volumes:
        - name: config
          configMap:
            name: loki-config
        - name: storage
          emptyDir: {}

---
apiVersion: v1
kind: Service
metadata:
  name: loki
  namespace: foodies
spec:
  selector:
    app: loki
  ports:
    - name: http
      port: 3100
      targetPort: 3100
```

**Promtail for Log Collection:**

```yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: promtail
  namespace: foodies
spec:
  selector:
    matchLabels:
      app: promtail
  template:
    metadata:
      labels:
        app: promtail
    spec:
      serviceAccountName: promtail
      containers:
        - name: promtail
          image: grafana/promtail:2.9.3
          args:
            - -config.file=/etc/promtail/promtail.yaml
          volumeMounts:
            - name: config
              mountPath: /etc/promtail
            - name: varlog
              mountPath: /var/log
            - name: varlibdockercontainers
              mountPath: /var/lib/docker/containers
              readOnly: true
          resources:
            requests:
              memory: "64Mi"
              cpu: "50m"
            limits:
              memory: "128Mi"
              cpu: "100m"
      volumes:
        - name: config
          configMap:
            name: promtail-config
        - name: varlog
          hostPath:
            path: /var/log
        - name: varlibdockercontainers
          hostPath:
            path: /var/lib/docker/containers
```

---

## Alerting

### Alert Rules

**Create `k8s/infrastructure/prometheus-rules.yaml`:**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-rules
  namespace: foodies
data:
  alerts.yml: |
    groups:
      - name: service_alerts
        interval: 30s
        rules:
          # High error rate
          - alert: HighErrorRate
            expr: |
              sum(rate(http_server_requests_count{status=~"5.."}[5m])) by (service)
              /
              sum(rate(http_server_requests_count[5m])) by (service)
              > 0.05
            for: 5m
            labels:
              severity: critical
            annotations:
              summary: "High error rate on {{ $labels.service }}"
              description: "{{ $labels.service }} has error rate above 5% (current: {{ $value | humanizePercentage }})"

          # High latency
          - alert: HighLatency
            expr: |
              histogram_quantile(0.95,
                sum(rate(http_server_requests_duration_bucket[5m])) by (service, le)
              ) > 1
            for: 5m
            labels:
              severity: warning
            annotations:
              summary: "High latency on {{ $labels.service }}"
              description: "{{ $labels.service }} p95 latency is above 1s (current: {{ $value }}s)"

          # Service down
          - alert: ServiceDown
            expr: up{job=~"webapp|menu|basket|profile"} == 0
            for: 1m
            labels:
              severity: critical
            annotations:
              summary: "Service {{ $labels.job }} is down"
              description: "{{ $labels.job }} has been down for more than 1 minute"

      - name: infrastructure_alerts
        interval: 30s
        rules:
          # High memory usage
          - alert: HighMemoryUsage
            expr: |
              (jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) > 0.9
            for: 5m
            labels:
              severity: warning
            annotations:
              summary: "High memory usage on {{ $labels.service }}"
              description: "{{ $labels.service }} heap memory usage is above 90% (current: {{ $value | humanizePercentage }})"

          # RabbitMQ queue depth
          - alert: HighQueueDepth
            expr: rabbitmq_queue_messages > 1000
            for: 10m
            labels:
              severity: warning
            annotations:
              summary: "High queue depth in RabbitMQ"
              description: "Queue {{ $labels.queue }} has more than 1000 messages (current: {{ $value }})"

          # Database connection pool exhaustion
          - alert: DatabaseConnectionPoolExhausted
            expr: |
              hikaricp_connections_active / hikaricp_connections_max > 0.9
            for: 5m
            labels:
              severity: critical
            annotations:
              summary: "Database connection pool nearly exhausted"
              description: "Connection pool for {{ $labels.pool }} is above 90% utilization"

          # Redis memory usage
          - alert: HighRedisMemory
            expr: redis_memory_used_bytes / redis_memory_max_bytes > 0.9
            for: 5m
            labels:
              severity: warning
            annotations:
              summary: "High Redis memory usage"
              description: "Redis memory usage is above 90% (current: {{ $value | humanizePercentage }})"

      - name: business_alerts
        interval: 1m
        rules:
          # High authentication failure rate
          - alert: HighAuthFailureRate
            expr: |
              sum(rate(foodies_auth_login_failure[5m]))
              /
              sum(rate(foodies_auth_login_success[5m]) + rate(foodies_auth_login_failure[5m]))
              > 0.2
            for: 10m
            labels:
              severity: warning
            annotations:
              summary: "High authentication failure rate"
              description: "Authentication failure rate is above 20% (current: {{ $value | humanizePercentage }})"

          # Low event processing rate
          - alert: LowEventProcessingRate
            expr: rate(foodies_profile_events_processed[5m]) < 0.1
            for: 15m
            labels:
              severity: warning
            annotations:
              summary: "Low event processing rate in profile service"
              description: "Profile service is processing less than 0.1 events/second"
```

---

## Implementation Roadmap

### Phase 1: Metrics Foundation (Week 1-2)

**Goal:** Export basic metrics from all services

- [ ] Add Micrometer dependencies to all services
- [ ] Configure Prometheus registry in each service
- [ ] Expose `/metrics` endpoint
- [ ] Deploy Prometheus to Kubernetes
- [ ] Configure Prometheus scraping
- [ ] Verify metrics collection

**Deliverables:**
- Metrics endpoints functional on all services
- Prometheus scraping successfully
- Basic JVM and HTTP metrics available

### Phase 2: Custom Metrics (Week 2-3)

**Goal:** Add business-specific metrics

- [ ] Define custom metrics for each service
- [ ] Instrument business operations
- [ ] Add metric tags for dimensionality
- [ ] Document metric meanings
- [ ] Create metric inventory

**Deliverables:**
- Business metrics defined and instrumented
- Metric documentation complete

### Phase 3: Dashboards (Week 3-4)

**Goal:** Visualize metrics with Grafana

- [ ] Deploy Grafana to Kubernetes
- [ ] Configure Prometheus datasource
- [ ] Create service overview dashboards
- [ ] Create infrastructure dashboard
- [ ] Create business metrics dashboard
- [ ] Export dashboard JSON for version control

**Deliverables:**
- Grafana accessible with dashboards
- Dashboard templates in Git

### Phase 4: Alerting (Week 4-5)

**Goal:** Proactive problem detection

- [ ] Define alert rules
- [ ] Configure Prometheus Alertmanager
- [ ] Set up notification channels (Slack, email)
- [ ] Test alert firing and resolution
- [ ] Document alert runbooks

**Deliverables:**
- Alert rules configured
- Notifications working
- Runbooks documented

### Phase 5: Distributed Tracing (Week 5-7)

**Goal:** Implement end-to-end tracing

- [ ] Follow [DISTRIBUTED_TRACING.md](fleet-file://mmglq7uf96d8i197ro8d/Users/simonvergauwen/Developer/foodies/specs/DISTRIBUTED_TRACING.md?type=file&root=%252F) specification
- [ ] Add OpenTelemetry dependencies
- [ ] Instrument HTTP clients/servers
- [ ] Instrument RabbitMQ publishers/consumers
- [ ] Deploy Jaeger
- [ ] Verify end-to-end trace propagation
- [ ] Integrate trace IDs into logs

**Deliverables:**
- Distributed tracing operational
- Jaeger UI accessible
- Logs correlated with traces

### Phase 6: Log Aggregation (Week 7-8)

**Goal:** Centralized log management

- [ ] Add structured logging (JSON)
- [ ] Deploy Loki
- [ ] Deploy Promtail
- [ ] Configure log retention
- [ ] Add Loki datasource to Grafana
- [ ] Create log exploration dashboards

**Deliverables:**
- Centralized logging operational
- Logs searchable in Grafana

### Phase 7: Production Readiness (Week 8-9)

**Goal:** Harden observability stack

- [ ] Add persistent storage for Prometheus
- [ ] Configure metric retention policies
- [ ] Set up backup/restore procedures
- [ ] Load test observability stack
- [ ] Document troubleshooting procedures
- [ ] Create observability runbook

**Deliverables:**
- Observability stack production-ready
- Documentation complete

---

## Kubernetes Updates

### Service Deployment Updates

All service deployments need to add:

```yaml
env:
  - name: LOG_FORMAT
    value: "json"  # Enable JSON logging in production
  - name: SERVICE_NAME
    value: "webapp"  # or menu, basket, profile
```

### Resource Limits for Observability Stack

```yaml
# Prometheus
resources:
  requests:
    memory: "256Mi"
    cpu: "100m"
  limits:
    memory: "1Gi"
    cpu: "500m"

# Grafana
resources:
  requests:
    memory: "128Mi"
    cpu: "100m"
  limits:
    memory: "256Mi"
    cpu: "500m"

# Loki
resources:
  requests:
    memory: "256Mi"
    cpu: "100m"
  limits:
    memory: "512Mi"
    cpu: "500m"

# Jaeger (from DISTRIBUTED_TRACING.md)
resources:
  requests:
    memory: "256Mi"
    cpu: "100m"
  limits:
    memory: "512Mi"
    cpu: "500m"
```

---

## Testing Strategy

### Metrics Testing

```kotlin
class MetricsSpec {
    @Test
    fun `metrics endpoint returns prometheus format`() = testApplication {
        application {
            installMetrics(MetricsConfig(serviceName = "test"))
        }

        val response = client.get("/metrics")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("http_server_requests_count"))
    }

    @Test
    fun `custom counter increments correctly`() {
        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val counter = registry.counter("test.counter")

        counter.increment()
        counter.increment()

        assertEquals(2.0, counter.count())
    }
}
```

### Alert Testing

```bash
# Test alert firing
curl -X POST http://localhost:9090/api/v1/alerts

# Verify Alertmanager receives alerts
curl http://localhost:9093/api/v1/alerts
```

---

## Operational Runbooks

### Dashboard Access

- **Grafana:** `http://grafana.foodies.local:3000` (user: admin, pass: admin)
- **Prometheus:** `http://prometheus.foodies.local:9090`
- **Jaeger:** `http://jaeger.foodies.local:16686`

### Common Troubleshooting

#### No Metrics Appearing in Prometheus

1. Check service `/metrics` endpoint: `curl http://service:port/metrics`
2. Verify Prometheus scrape config: `kubectl get cm prometheus-config -n foodies -o yaml`
3. Check Prometheus targets: Prometheus UI → Status → Targets
4. Verify pod labels match scrape config

#### High Memory Usage Alert

1. Check JVM heap usage in Grafana
2. Review GC metrics for excessive collection
3. Check for memory leaks using heap dump
4. Consider increasing memory limits
5. Review application code for resource leaks

#### Service Latency Spike

1. Check distributed trace in Jaeger
2. Identify slow span in trace
3. Review database query performance
4. Check external service dependencies
5. Review application logs for errors

#### RabbitMQ Queue Depth Growing

1. Check profile service health
2. Review event processing rate metrics
3. Check for failed event processing (nacks)
4. Review profile service logs for errors
5. Consider scaling profile service replicas

---

## Cost Optimization

### Metric Retention

- **Default:** 7 days in Prometheus
- **Long-term:** Export to S3 or similar (not implemented)
- **Estimated storage:** ~10GB for 7 days with current metrics

### Log Retention

- **Default:** 7 days in Loki
- **Estimated storage:** ~5GB for 7 days
- **Optimization:** Filter debug logs in production

### Resource Allocation

Total observability stack resource usage:
- **Memory:** ~2GB (Prometheus: 1GB, others: ~1GB)
- **CPU:** ~1 core (mostly idle)
- **Storage:** ~15GB (metrics + logs)

---

## Security Considerations

### Metrics Endpoint Protection

- Consider authentication for `/metrics` endpoint in production
- Use Kubernetes NetworkPolicy to restrict Prometheus access
- Avoid exposing sensitive data in metric labels

### Dashboard Access Control

- Configure Grafana authentication
- Use RBAC for dashboard editing
- Enable audit logging

### Log Sanitization

- Avoid logging sensitive data (passwords, tokens, PII)
- Implement log scrubbing for sensitive patterns
- Use structured logging to control field visibility

---

## References

- [Micrometer Documentation](https://micrometer.io/docs)
- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/)
- [Loki Documentation](https://grafana.com/docs/loki/)
- [OpenTelemetry Specification](https://opentelemetry.io/docs/)
- [Cohort Health Checks](https://github.com/sksamuel/cohort)
- [Foodies Health Check Spec](fleet-file://mmglq7uf96d8i197ro8d/Users/simonvergauwen/Developer/foodies/specs/HEALTH_CHECK_SPECIFICATION.md?type=file&root=%252F)
- [Foodies Distributed Tracing Spec](fleet-file://mmglq7uf96d8i197ro8d/Users/simonvergauwen/Developer/foodies/specs/DISTRIBUTED_TRACING.md?type=file&root=%252F)

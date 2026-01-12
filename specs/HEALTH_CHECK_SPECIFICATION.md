# Health Check Specification

## Overview

This specification defines health check, liveness, and readiness probe implementations for the Foodies microservices platform using the [Cohort](https://github.com/sksamuel/cohort) library. Cohort provides Spring Actuator-style health checks for Ktor with built-in support for Kubernetes probes, database monitoring, and various system health checks.

## Current State Analysis

### Existing Implementation

All three services currently implement a minimal `/healthz` endpoint:

```kotlin
fun Route.healthz() = get("/healthz") { call.respond(HttpStatusCode.OK) }
```

**Issues with Current Implementation:**

1. **No K8s probes configured** - The K8s deployment manifests have no liveness, readiness, or startup probes defined
2. **No dependency verification** - Health endpoint doesn't verify database or RabbitMQ connectivity
3. **No distinction between liveness and readiness** - Single endpoint cannot express different health semantics
4. **No startup probe support** - Services with slow initialization (Flyway migrations) may be killed during startup

### Service Dependencies

| Service | Database | RabbitMQ | External HTTP |
|---------|----------|----------|---------------|
| webapp  | No       | No       | menu service  |
| menu    | Yes      | No       | No            |
| profile | Yes      | Yes      | No            |

---

## Proposed Health Check Architecture

### Three-Probe Strategy

| Probe Type | Endpoint | Purpose | Dependency Checks |
|------------|----------|---------|-------------------|
| **Startup** | `/healthz/startup` | Container initialization complete | None (fast response) |
| **Liveness** | `/healthz/liveness` | Process is running and not deadlocked | Thread deadlock detection |
| **Readiness** | `/healthz/readiness` | Ready to accept traffic | Database, message broker, HTTP services |

### Design Principles

1. **Liveness probes must never check external dependencies** - A failing database should not restart the container
2. **Readiness probes control traffic routing** - Temporarily remove unhealthy pods from load balancer
3. **Startup probes protect slow-starting containers** - Prevent premature liveness failures during migrations
4. **Conservative timeouts prevent cascading failures** - Generous thresholds avoid mass restarts

---

## Dependencies

### Gradle Configuration

Add the following dependencies to `server-shared/build.gradle.kts`:

```kotlin
dependencies {
    // Cohort core
    api("com.sksamuel.cohort:cohort-ktor:2.8.2")
    api("com.sksamuel.cohort:cohort-api:2.8.2")

    // HikariCP health checks (for database connection pool monitoring)
    api("com.sksamuel.cohort:cohort-hikari:2.8.2")
}
```

For the profile service with RabbitMQ, add to `profile/build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.sksamuel.cohort:cohort-rabbit:2.8.2")
}
```

---

## Code Implementation

### Shared Health Module

Create utility functions in `server-shared`:

**File:** `server-shared/src/main/kotlin/io/ktor/foodies/server/Health.kt`

```kotlin
package io.ktor.foodies.server

import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.healthcheck.http.HttpHealthCheck
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import com.sksamuel.cohort.threads.ThreadDeadlockHealthCheck
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Creates default liveness checks that verify internal process health.
 * NEVER add external dependency checks here.
 */
fun defaultLivenessChecks(): HealthCheckRegistry =
    HealthCheckRegistry(Dispatchers.Default) {
        // Detect deadlocked threads that would indicate a hung process
        register(ThreadDeadlockHealthCheck(), interval = 1.minutes)
    }

/**
 * Creates a health check registry for database connectivity.
 * Verifies HikariCP connection pool has available connections.
 *
 * @param dataSource The HikariCP data source to monitor
 * @param minConnections Minimum connections required to be healthy (default: 1)
 * @param interval How often to check (default: 5 seconds)
 */
fun databaseHealthCheckRegistry(
    dataSource: HikariDataSource,
    minConnections: Int = 1,
    interval: Duration = 5.seconds
): HealthCheckRegistry = HealthCheckRegistry(Dispatchers.IO) {
    register(
        HikariConnectionsHealthCheck(dataSource, minConnections),
        interval = interval
    )
}

/**
 * Creates a health check for HTTP service dependencies.
 * Used by webapp to verify menu service is reachable.
 *
 * @param url The health check URL of the dependency
 * @param serviceName Human-readable name for logging
 * @param timeout Request timeout
 * @param interval How often to check
 */
fun httpServiceHealthCheck(
    url: String,
    serviceName: String,
    timeout: Duration = 3.seconds,
    interval: Duration = 5.seconds
): HealthCheckRegistry = HealthCheckRegistry(Dispatchers.IO) {
    register(
        name = serviceName,
        healthcheck = HttpHealthCheck(
            url = url,
            connectionTimeout = timeout,
            readTimeout = timeout
        ),
        interval = interval
    )
}
```

### RabbitMQ Health Check for Profile Service

**File:** `profile/src/main/kotlin/io/ktor/foodies/server/RabbitHealthCheck.kt`

```kotlin
package io.ktor.foodies.server

import com.rabbitmq.client.Connection
import com.sksamuel.cohort.HealthCheck
import com.sksamuel.cohort.HealthCheckResult

/**
 * Health check that verifies RabbitMQ connection is open and functional.
 * Uses Cohort's HealthCheck interface for integration with the health check registry.
 */
class RabbitConnectionHealthCheck(
    private val connection: Connection
) : HealthCheck {

    override val name: String = "rabbitmq-connection"

    override suspend fun check(): HealthCheckResult {
        return if (connection.isOpen) {
            HealthCheckResult.healthy("RabbitMQ connection is open")
        } else {
            HealthCheckResult.unhealthy("RabbitMQ connection is closed", null)
        }
    }
}
```

---

## Service-Specific Implementations

### Menu Service

**File:** `menu/src/main/kotlin/io/ktor/foodies/menu/App.kt`

```kotlin
package io.ktor.foodies.menu

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import com.zaxxer.hikari.HikariDataSource
import io.ktor.foodies.server.defaultLivenessChecks
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration.Companion.seconds

fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        app(module(config))
    }.start(wait = true)
}

fun Application.app(module: MenuModule) {
    install(ContentNegotiation) { json() }

    // Configure Cohort health checks
    install(Cohort) {
        verboseHealthCheckResponse = true

        // Startup probe - empty registry for fast response
        healthcheck("/healthz/startup", HealthCheckRegistry(Dispatchers.Default))

        // Liveness probe - internal process health only
        healthcheck("/healthz/liveness", defaultLivenessChecks())

        // Readiness probe - check database connectivity
        healthcheck("/healthz/readiness", HealthCheckRegistry(Dispatchers.IO) {
            register(
                name = "database",
                healthcheck = HikariConnectionsHealthCheck(module.dataSource, minConnections = 1),
                interval = 5.seconds
            )
        })
    }

    routing {
        menuRoutes(module.menuService)
    }
}
```

**Update MenuModule to expose HikariDataSource:**

```kotlin
data class MenuModule(
    val menuService: MenuService,
    val dataSource: HikariDataSource  // Expose for health checks
)
```

### Profile Service

**File:** `profile/src/main/kotlin/io/ktor/foodies/server/ProfileApp.kt`

```kotlin
package io.ktor.foodies.server

import com.rabbitmq.client.Connection
import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlin.time.Duration.Companion.seconds

fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        app(module(config))
    }.start(wait = true)
}

fun Application.app(module: ProfileModule) {
    module.consumers.forEach { it.process().launchIn(this) }

    // Configure Cohort health checks
    install(Cohort) {
        verboseHealthCheckResponse = true

        // Startup probe - empty registry for fast response
        healthcheck("/healthz/startup", HealthCheckRegistry(Dispatchers.Default))

        // Liveness probe - internal process health only
        healthcheck("/healthz/liveness", defaultLivenessChecks())

        // Readiness probe - check database and RabbitMQ connectivity
        healthcheck("/healthz/readiness", HealthCheckRegistry(Dispatchers.IO) {
            register(
                name = "database",
                healthcheck = HikariConnectionsHealthCheck(module.dataSource, minConnections = 1),
                interval = 5.seconds
            )
            register(
                name = "rabbitmq",
                healthcheck = RabbitConnectionHealthCheck(module.connection),
                interval = 5.seconds
            )
        })
    }

    routing {
        // No additional routes needed - Cohort handles /healthz/*
    }
}
```

**Update ProfileModule to expose dependencies:**

```kotlin
class ProfileModule(
    val consumers: List<Consumer>,
    val dataSource: HikariDataSource,  // Expose for health checks
    val connection: Connection          // Expose for health checks
)
```

### Webapp Service

**File:** `webapp/src/main/kotlin/io/ktor/foodies/server/WebApp.kt`

```kotlin
package io.ktor.foodies.server

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.healthcheck.http.HttpHealthCheck
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.foodies.server.menu.HttpMenuService
import io.ktor.foodies.server.menu.menuRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration.Companion.seconds

fun main() {
    val env = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = env.host, port = env.port) {
        val httpClient = HttpClient(Apache5) {
            install(ContentNegotiation) { json() }
        }
        monitor.subscribe(ApplicationStopped) { httpClient.close() }

        security(env.security, httpClient)
        app(env, httpClient)
    }.start(wait = true)
}

fun Application.app(config: Config, httpClient: HttpClient) {
    val menuService = HttpMenuService(config.menu.baseUrl, httpClient)

    // Configure Cohort health checks
    install(Cohort) {
        verboseHealthCheckResponse = true

        // Startup probe - empty registry for fast response
        healthcheck("/healthz/startup", HealthCheckRegistry(Dispatchers.Default))

        // Liveness probe - internal process health only
        healthcheck("/healthz/liveness", defaultLivenessChecks())

        // Readiness probe - check menu service connectivity
        healthcheck("/healthz/readiness", HealthCheckRegistry(Dispatchers.IO) {
            register(
                name = "menu-service",
                healthcheck = HttpHealthCheck(
                    url = "${config.menu.baseUrl}/healthz/readiness",
                    connectionTimeout = 3.seconds,
                    readTimeout = 3.seconds
                ),
                interval = 5.seconds
            )
        })
    }

    routing {
        staticResources("/static", "static")
        home()
        menuRoutes(menuService)
    }
}
```

---

## Kubernetes Configuration

### Probe Timing Configuration

| Parameter | Startup | Liveness | Readiness |
|-----------|---------|----------|-----------|
| `initialDelaySeconds` | 0 | 0 | 0 |
| `periodSeconds` | 2 | 10 | 5 |
| `timeoutSeconds` | 3 | 3 | 5 |
| `failureThreshold` | 30 | 3 | 3 |
| `successThreshold` | 1 | 1 | 1 |

**Rationale:**

- **Startup probe** runs every 2s with 30 failures allowed = 60s max startup time (handles Flyway migrations)
- **Liveness probe** runs every 10s with low timeout - frequent enough to detect deadlocks, not so frequent as to waste resources
- **Readiness probe** runs every 5s with higher timeout to accommodate dependency latency
- **failureThreshold of 3** for liveness/readiness prevents transient failures from causing restarts

### Menu Service Deployment

**File:** `k8s/services/menu.yaml`

```yaml
apiVersion: v1
kind: Service
metadata:
  name: menu
  namespace: foodies
spec:
  ports:
    - name: http
      port: 8082
      targetPort: 8082
  selector:
    app: menu
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: menu
  namespace: foodies
spec:
  replicas: 2
  selector:
    matchLabels:
      app: menu
  template:
    metadata:
      labels:
        app: menu
    spec:
      containers:
        - name: menu
          image: foodies-menu:latest
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8082
              name: http
          resources:
            requests:
              memory: "256Mi"
              cpu: "100m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          startupProbe:
            httpGet:
              path: /healthz/startup
              port: 8082
            periodSeconds: 2
            timeoutSeconds: 3
            failureThreshold: 30
          livenessProbe:
            httpGet:
              path: /healthz/liveness
              port: 8082
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /healthz/readiness
              port: 8082
            periodSeconds: 5
            timeoutSeconds: 5
            failureThreshold: 3
          env:
            - name: HOST
              value: "0.0.0.0"
            - name: PORT
              value: "8082"
            - name: DB_URL
              value: jdbc:postgresql://menu-postgres:5432/foodies-menu-database
            - name: DB_USERNAME
              valueFrom:
                secretKeyRef:
                  name: postgres-credentials
                  key: POSTGRES_USER
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: postgres-credentials
                  key: POSTGRES_PASSWORD
```

### Profile Service Deployment

**File:** `k8s/services/profile.yaml`

```yaml
apiVersion: v1
kind: Service
metadata:
  name: profile
  namespace: foodies
spec:
  ports:
    - name: http
      port: 8081
      targetPort: 8081
  selector:
    app: profile
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: profile
  namespace: foodies
spec:
  replicas: 2
  selector:
    matchLabels:
      app: profile
  template:
    metadata:
      labels:
        app: profile
    spec:
      containers:
        - name: profile
          image: foodies-profile:latest
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8081
              name: http
          resources:
            requests:
              memory: "256Mi"
              cpu: "100m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          startupProbe:
            httpGet:
              path: /healthz/startup
              port: 8081
            periodSeconds: 2
            timeoutSeconds: 3
            failureThreshold: 30
          livenessProbe:
            httpGet:
              path: /healthz/liveness
              port: 8081
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /healthz/readiness
              port: 8081
            periodSeconds: 5
            timeoutSeconds: 5
            failureThreshold: 3
          env:
            - name: HOST
              value: "0.0.0.0"
            - name: PORT
              value: "8081"
            - name: DB_URL
              value: jdbc:postgresql://profile-postgres:5432/foodies-profile-database
            - name: DB_USERNAME
              valueFrom:
                secretKeyRef:
                  name: postgres-credentials
                  key: POSTGRES_USER
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: postgres-credentials
                  key: POSTGRES_PASSWORD
            - name: RABBITMQ_HOST
              value: rabbitmq
            - name: RABBITMQ_PORT
              value: "5672"
            - name: RABBITMQ_USERNAME
              valueFrom:
                secretKeyRef:
                  name: rabbitmq-credentials
                  key: RABBITMQ_USERNAME
            - name: RABBITMQ_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: rabbitmq-credentials
                  key: RABBITMQ_PASSWORD
            - name: RABBITMQ_QUEUE
              valueFrom:
                configMapKeyRef:
                  name: foodies-config
                  key: RABBITMQ_QUEUE
```

### Webapp Service Deployment

**File:** `k8s/services/webapp.yaml`

```yaml
apiVersion: v1
kind: Service
metadata:
  name: webapp
  namespace: foodies
spec:
  type: ClusterIP
  ports:
    - name: http
      port: 8080
      targetPort: 8080
  selector:
    app: webapp
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: webapp
  namespace: foodies
spec:
  replicas: 2
  selector:
    matchLabels:
      app: webapp
  template:
    metadata:
      labels:
        app: webapp
    spec:
      containers:
        - name: webapp
          image: foodies-webapp:latest
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8080
              name: http
          resources:
            requests:
              memory: "256Mi"
              cpu: "100m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          startupProbe:
            httpGet:
              path: /healthz/startup
              port: 8080
            periodSeconds: 2
            timeoutSeconds: 3
            failureThreshold: 30
          livenessProbe:
            httpGet:
              path: /healthz/liveness
              port: 8080
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /healthz/readiness
              port: 8080
            periodSeconds: 5
            timeoutSeconds: 5
            failureThreshold: 3
          env:
            - name: HOST
              value: "0.0.0.0"
            - name: PORT
              value: "8080"
            - name: AUTH_ISSUER
              value: http://keycloak:8000/auth/realms/foodies-keycloak
            - name: AUTH_CLIENT_ID
              value: foodies
            - name: AUTH_CLIENT_SECRET
              valueFrom:
                secretKeyRef:
                  name: webapp-auth
                  key: AUTH_CLIENT_SECRET
            - name: MENU_BASE_URL
              value: http://menu:8082
```

---

## Cohort Features and Benefits

### Built-in Health Checks Used

| Check | Module | Purpose |
|-------|--------|---------|
| `ThreadDeadlockHealthCheck` | cohort-api | Detects deadlocked threads (liveness) |
| `HikariConnectionsHealthCheck` | cohort-hikari | Verifies database pool connectivity |
| `HttpHealthCheck` | cohort-api | Checks HTTP service dependencies |

### Additional Cohort Capabilities (Optional)

Cohort provides additional features that can be enabled for enhanced observability:

```kotlin
install(Cohort) {
    // JVM and system information endpoints
    operatingSystem = true  // GET /cohort/operatingsystem
    jvmInfo = true          // GET /cohort/jvminfo
    sysprops = true         // GET /cohort/sysprops

    // Debugging endpoints (disable in production)
    heapDump = false        // GET /cohort/heapdump
    threadDump = true       // GET /cohort/threaddump

    // DataSource monitoring
    dataSources = listOf(HikariDataSourceManager(dataSource))  // GET /cohort/datasources

    // Runtime logging management (requires cohort-logback)
    logManager = LogbackManager  // GET/PUT /cohort/logging
}
```

### Health Check Response Format

**Verbose Response (default):**
```json
{
  "name": "database",
  "healthy": true,
  "lastCheck": "2024-01-15T10:30:00.000Z",
  "message": "HikariCP has 5 active connections (minimum: 1)",
  "cause": null,
  "consecutiveSuccesses": 42,
  "consecutiveFailures": 0
}
```

**Simple Response** (when `verboseHealthCheckResponse = false`):
- HTTP 200: `"OK"`
- HTTP 503: `"Service unavailable"`

### Micrometer Integration (Optional)

For metrics export to monitoring systems:

```kotlin
// Add dependency: com.sksamuel.cohort:cohort-micrometer:2.8.2

val micrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
val healthcheckRegistry = HealthCheckRegistry(Dispatchers.Default) {
    register("database", HikariConnectionsHealthCheck(dataSource, 1), 5.seconds)
}

CohortMetrics(healthcheckRegistry).bindTo(micrometerRegistry)
```

Each health check emits a `cohort.healthcheck` metric with tags for name, type, and healthy status.

---

## Best Practices Summary

### What Each Probe Should Check

| Probe | Should Check | Should NOT Check |
|-------|-------------|------------------|
| **Startup** | HTTP server is accepting connections | Any dependencies |
| **Liveness** | Thread deadlocks (via Cohort) | Database, message broker, external services |
| **Readiness** | All required dependencies are healthy | N/A (check everything needed to serve traffic) |

### Anti-Patterns to Avoid

1. **Checking database in liveness probe** - Causes container restart when DB has transient issues
2. **Short timeouts on readiness** - Leads to traffic flapping during normal latency spikes
3. **Missing startup probe** - Services with migrations get killed during initialization
4. **Shared dependency failure cascade** - All pods failing readiness simultaneously
5. **Not using health check registries** - Cohort's registry pattern ensures checks run on schedule

### Resilience Patterns Implemented

1. **Graceful degradation** - Failed dependency removes pod from LB, doesn't restart it
2. **Startup protection** - 60-second window for migrations and initialization
3. **Conservative thresholds** - 3 failures before action prevents transient issues from escalating
4. **Scheduled health checks** - Cohort runs checks on intervals, caching results
5. **Multiple replicas** - All services have 2+ replicas for high availability

---

## Implementation Checklist

### Dependencies

- [ ] Add `cohort-ktor` and `cohort-api` to `server-shared/build.gradle.kts`
- [ ] Add `cohort-hikari` to `server-shared/build.gradle.kts`
- [ ] Add `cohort-rabbit` to `profile/build.gradle.kts` (if using Cohort's RabbitMQ check)

### Code Changes

- [ ] Create `server-shared/src/main/kotlin/io/ktor/foodies/server/Health.kt` with utility functions
- [ ] Create `profile/src/main/kotlin/io/ktor/foodies/server/RabbitHealthCheck.kt`
- [ ] Update `MenuModule` to expose `HikariDataSource`
- [ ] Update `ProfileModule` to expose `HikariDataSource` and `Connection`
- [ ] Update `menu/App.kt` to install Cohort with three probes
- [ ] Update `profile/ProfileApp.kt` to install Cohort with three probes
- [ ] Update `webapp/WebApp.kt` to install Cohort with three probes
- [ ] Remove old `healthz()` route functions
- [ ] Write unit tests for health check configurations
- [ ] Write integration tests for readiness checks

### Kubernetes Changes

- [ ] Update `k8s/services/menu.yaml` with probes (using `/healthz/startup`, `/healthz/liveness`, `/healthz/readiness`)
- [ ] Update `k8s/services/profile.yaml` with probes
- [ ] Update `k8s/services/webapp.yaml` with probes
- [ ] Test probe behavior in local K8s cluster
- [ ] Verify startup timing with Flyway migrations
- [ ] Test failure scenarios (kill DB, kill RabbitMQ)

### Validation Tests

1. **Startup test**: Deploy service, verify it becomes ready within 60s
2. **Liveness test**: Confirm container NOT restarted when DB is temporarily unavailable
3. **Readiness test**: Confirm pod removed from service endpoints when DB is down
4. **Recovery test**: Confirm pod returns to service when DB recovers
5. **Cascade test**: Kill shared dependency, verify not all pods restart simultaneously

---

## Appendix: Response Examples

### Startup Probe Response
```
HTTP 200 OK
```

### Liveness Probe Response (Healthy)
```json
{
  "status": "Healthy",
  "checks": [
    {
      "name": "com.sksamuel.cohort.threads.ThreadDeadlockHealthCheck",
      "healthy": true,
      "lastCheck": "2024-01-15T10:30:00.000Z",
      "message": "No deadlocked threads detected",
      "consecutiveSuccesses": 100,
      "consecutiveFailures": 0
    }
  ]
}
```

### Readiness Probe Response (Healthy)
```json
{
  "status": "Healthy",
  "checks": [
    {
      "name": "database",
      "healthy": true,
      "lastCheck": "2024-01-15T10:30:00.000Z",
      "message": "HikariCP has 5 active connections (minimum: 1)",
      "consecutiveSuccesses": 50,
      "consecutiveFailures": 0
    },
    {
      "name": "rabbitmq",
      "healthy": true,
      "lastCheck": "2024-01-15T10:30:00.000Z",
      "message": "RabbitMQ connection is open",
      "consecutiveSuccesses": 50,
      "consecutiveFailures": 0
    }
  ]
}
```

### Readiness Probe Response (Unhealthy)
```json
{
  "status": "Unhealthy",
  "checks": [
    {
      "name": "database",
      "healthy": true,
      "lastCheck": "2024-01-15T10:30:00.000Z",
      "message": "HikariCP has 3 active connections (minimum: 1)",
      "consecutiveSuccesses": 50,
      "consecutiveFailures": 0
    },
    {
      "name": "rabbitmq",
      "healthy": false,
      "lastCheck": "2024-01-15T10:30:05.000Z",
      "message": "RabbitMQ connection is closed",
      "cause": "com.rabbitmq.client.ShutdownSignalException: connection error",
      "consecutiveSuccesses": 0,
      "consecutiveFailures": 3
    }
  ]
}
```

---

## Migration from Current Implementation

To migrate from the existing `healthz()` function:

1. Remove the old route registration:
   ```kotlin
   // Remove this from routing blocks:
   healthz()
   ```

2. Add Cohort installation with three dedicated probe endpoints as shown in the service implementations above

3. Configure Kubernetes deployment manifests to use the new probe endpoints:
   - `/healthz/startup` - For Kubernetes startup probes
   - `/healthz/liveness` - For Kubernetes liveness probes
   - `/healthz/readiness` - For Kubernetes readiness probes

# Distributed Session Storage Specification

## Overview

This document specifies the implementation of Redis-based distributed session storage for the WebApp service. The current implementation uses in-memory cookie-based sessions, which does not support horizontal scaling across multiple WebApp replicas in Kubernetes. This specification addresses the TODO comment in [Security.kt:46](fleet-file://mmglq7uf96d8i197ro8d/Users/simonvergauwen/Developer/foodies/webapp/src/main/kotlin/io/ktor/foodies/server/Security.kt?type=file&root=%252F) by implementing a Redis session store.

## Current State

### Deployment Configuration
- **WebApp Deployment:** 2 replicas (k8s/services/webapp.yaml:21)
- **Redis Deployment:** 1 replica (k8s/databases/redis.yaml:20)
- **Redis Service:** Available at `redis:6379` within the cluster

### Current Session Implementation
**Location:** [webapp/src/main/kotlin/io/ktor/foodies/server/Security.kt:45-52](fleet-file://mmglq7uf96d8i197ro8d/Users/simonvergauwen/Developer/foodies/webapp/src/main/kotlin/io/ktor/foodies/server/Security.kt?type=file&root=%252F)

```kotlin
// TODO redis for distributed session, or sticky load balancing
cookie<UserSession>("USER_SESSION") {
    cookie.secure = !this@security.developmentMode
    cookie.httpOnly = true
    cookie.extensions["SameSite"] = "lax"
}
```

**Session Data Class:**
```kotlin
@Serializable
data class UserSession(val idToken: String)
```

### Problems with Current Implementation
1. **Session Affinity Required:** Each user must be routed to the same pod for session persistence
2. **No Session Sharing:** Login state is isolated per pod
3. **Session Loss on Pod Restart:** Sessions are lost when pods are recreated
4. **Inefficient Load Balancing:** Cannot use round-robin distribution
5. **Scalability Limitation:** Cannot easily scale horizontally

## Redis Session Storage Architecture

### Design Goals
1. **Distributed Session Sharing:** All WebApp replicas share session state via Redis
2. **Session Persistence:** Sessions survive pod restarts and deployments
3. **Proper Load Balancing:** Support round-robin and other load balancing strategies
4. **Security:** Maintain secure cookie handling with signed session IDs
5. **Performance:** Minimal latency impact through efficient Redis operations
6. **Testing:** Comprehensive tests using TestContainers

### Session Storage Model

#### Session Key Structure
```
/session/{sessionId}
```

**Rationale:**
- Follows existing pattern from BasketRepository (`/basket/{buyerId}`)
- Namespace isolation prevents key collisions
- Simple and efficient for lookup operations

#### Session Data Serialization
Use Kotlinx Serialization (JSON) for consistency with the rest of the codebase:
```kotlin
@Serializable
data class UserSession(val idToken: String)
```

**Serialization:**
```kotlin
val data = Json.encodeToString(UserSession.serializer(), session)
redis.set(sessionKey(sessionId), data)
```

**Deserialization:**
```kotlin
val data = redis.get(sessionKey(sessionId)) ?: return null
return Json.decodeFromString<UserSession>(data)
```

#### Session TTL (Time-To-Live)
```kotlin
redis.setex(sessionKey(sessionId), sessionTTL, data)
```

**Configuration:**
- **Default TTL:** 3600 seconds (1 hour) - matches Keycloak access token lifespan
- **Configurable:** Can be overridden via environment variable `SESSION_TTL_SECONDS`
- **Sliding Window:** TTL is refreshed on each request that accesses the session

### Implementation Strategy

#### 1. Session Storage Interface

**Location:** `webapp/src/main/kotlin/io/ktor/foodies/server/session/`

**Files:**
```
session/
├── SessionStorage.kt          # Interface and implementations
├── RedisSessionStorage.kt     # Redis-based implementation
├── SessionConfig.kt           # Configuration data classes
└── SessionStoragePlugin.kt    # Ktor plugin integration
```

**SessionStorage.kt:**
```kotlin
package io.ktor.foodies.server.session

import kotlinx.serialization.Serializable

@Serializable
data class UserSession(val idToken: String)

interface SessionStorage {
    suspend fun write(id: String, value: UserSession)
    suspend fun read(id: String): UserSession?
    suspend fun invalidate(id: String)
}

/**
 * In-memory implementation for testing and development.
 * Not suitable for production use with multiple replicas.
 */
class InMemorySessionStorage : SessionStorage {
    private val sessions = mutableMapOf<String, UserSession>()

    override suspend fun write(id: String, value: UserSession) {
        sessions[id] = value
    }

    override suspend fun read(id: String): UserSession? {
        return sessions[id]
    }

    override suspend fun invalidate(id: String) {
        sessions.remove(id)
    }
}
```

**RedisSessionStorage.kt:**
```kotlin
package io.ktor.foodies.server.session

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.SetArgs
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import kotlinx.serialization.json.Json

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisSessionStorage(
    private val redis: RedisCoroutinesCommands<String, String>,
    private val ttlSeconds: Long = 3600
) : SessionStorage {

    private fun sessionKey(id: String): String = "/session/$id"

    override suspend fun write(id: String, value: UserSession) {
        val data = Json.encodeToString(UserSession.serializer(), value)
        redis.setex(sessionKey(id), ttlSeconds, data)
    }

    override suspend fun read(id: String): UserSession? {
        val data = redis.get(sessionKey(id)) ?: return null

        // Refresh TTL on read (sliding window expiration)
        redis.expire(sessionKey(id), ttlSeconds)

        return Json.decodeFromString<UserSession>(data)
    }

    override suspend fun invalidate(id: String) {
        redis.del(sessionKey(id))
    }
}
```

**SessionConfig.kt:**
```kotlin
package io.ktor.foodies.server.session

import kotlinx.serialization.Serializable

@Serializable
data class RedisSessionConfig(
    val host: String,
    val port: Int,
    val password: String = "",
    val ttlSeconds: Long = 3600
)
```

#### 2. Redis Client Setup

**Update:** `webapp/src/main/kotlin/io/ktor/foodies/server/Config.kt`

```kotlin
data class Config(
    val host: String,
    val port: Int,
    val security: Security,
    val menu: Menu,
    val basket: Basket,
    val redis: RedisSessionConfig  // Add this field
)

data class RedisSessionConfig(
    val host: String,
    val port: Int,
    val password: String = "",
    val ttlSeconds: Long = 3600
)
```

**Update:** `webapp/src/main/resources/application.yaml`

```yaml
config:
  host: "$HOST:0.0.0.0"
  port: "$PORT:8080"
  security:
    issuer: "$AUTH_ISSUER:http://localhost:8000/realms/foodies-keycloak"
    clientId: "$AUTH_CLIENT_ID:foodies"
    clientSecret: "$AUTH_CLIENT_SECRET:foodies_client_secret"
  menu:
    baseUrl: "$MENU_BASE_URL:http://localhost:8082"
  basket:
    baseUrl: "$BASKET_BASE_URL:http://localhost:8083"
  redis:
    host: "$REDIS_HOST:localhost"
    port: "$REDIS_PORT:6379"
    password: "$REDIS_PASSWORD:"
    ttlSeconds: "$SESSION_TTL_SECONDS:3600"
```

#### 3. Ktor Session Integration

**Update:** `webapp/src/main/kotlin/io/ktor/foodies/server/Security.kt`

Replace the current cookie-based session configuration:

```kotlin
suspend fun Application.security(config: Config.Security, httpClient: HttpClient) {
    val developmentMode = environment.developmentMode

    // Initialize Redis client for session storage
    val redisClient = RedisClient.create("redis://${config.redis.host}:${config.redis.port}")
    val redisConnection = redisClient.connect()
    val sessionStorage = RedisSessionStorage(
        redis = redisConnection.coroutines(),
        ttlSeconds = config.redis.ttlSeconds
    )

    // Cleanup Redis connection on application stop
    monitor.subscribe(ApplicationStopped) {
        redisConnection.close()
        redisClient.shutdown()
    }

    install(Sessions) {
        cookie<UserSession>("USER_SESSION", sessionStorage) {
            cookie.secure = !developmentMode
            cookie.httpOnly = true
            cookie.extensions["SameSite"] = "lax"
            // Session ID is stored in cookie, session data in Redis
            serializer = object : SessionSerializer<UserSession> {
                override fun serialize(session: UserSession): String {
                    // Cookie only contains the session ID
                    return generateSessionId()
                }

                override fun deserialize(text: String): UserSession {
                    // This won't be called - we load from Redis
                    throw NotImplementedError()
                }
            }
        }
    }

    // Rest of authentication configuration...
}
```

**Note:** Ktor's `Sessions` plugin supports custom storage backends through the `SessionStorage` interface, which we implement with `RedisSessionStorage`.

#### 4. Dependency Updates

**Update:** `webapp/build.gradle.kts`

Add Redis dependencies:

```kotlin
dependencies {
    // ... existing dependencies ...

    // Redis session storage
    implementation(libs.lettuce)
    implementation(libs.cohort.lettuce)
    implementation(libs.kotlinx.coroutines.reactor)

    // Testing
    testImplementation(libs.testcontainers.redis)
}
```

**Update:** `settings.gradle.kts`

Ensure Lettuce is available in the version catalog (already present):
```kotlin
lettuce = "6.5.5.RELEASE"
```

#### 5. Kubernetes Configuration

**Update:** `k8s/services/webapp.yaml`

Add Redis configuration environment variables:

```yaml
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
  - name: BASKET_BASE_URL
    value: http://basket:8083
  - name: REDIS_HOST              # Add this
    value: redis
  - name: REDIS_PORT              # Add this
    value: "6379"
  - name: SESSION_TTL_SECONDS     # Add this (optional)
    value: "3600"
```

**Readiness Check:** Add Redis health check

```yaml
readinessProbe:
  httpGet:
    path: /healthz/readiness
    port: 8080
  periodSeconds: 5
  timeoutSeconds: 5
  failureThreshold: 3
```

**Update:** `webapp/src/main/kotlin/io/ktor/foodies/server/WebApp.kt`

Add Redis health check to the readiness probe:

```kotlin
val readinessRegistry = HealthCheckRegistry(Dispatchers.IO) {
    // Existing checks
    register(
        name = "menu-service",
        check = EndpointHealthCheck { it.get("${config.menu.baseUrl}/healthz/readiness") }
    )
    register(
        name = "basket-service",
        check = EndpointHealthCheck { it.get("${config.basket.baseUrl}/healthz/readiness") }
    )

    // Add Redis health check
    register(
        name = "redis-session-storage",
        check = RedisHealthCheck(redisConnection.coroutines()),
        interval = 5.seconds,
        timeout = 3.seconds
    )
}
```

### Migration Path

#### Phase 1: Add Redis Session Storage Implementation
- [x] Create `SessionStorage` interface
- [x] Implement `RedisSessionStorage` with Lettuce
- [x] Implement `InMemorySessionStorage` for testing
- [x] Add configuration classes

#### Phase 2: Update WebApp Configuration
- [ ] Add Redis configuration to `Config.kt`
- [ ] Add Redis connection management to `Security.kt`
- [ ] Update `Sessions` plugin to use `RedisSessionStorage`
- [ ] Add Redis health check to readiness probe

#### Phase 3: Update Dependencies
- [ ] Add Lettuce to `webapp/build.gradle.kts`
- [ ] Add Cohort Lettuce for health checks
- [ ] Add TestContainers Redis for tests

#### Phase 4: Update Kubernetes Configuration
- [ ] Add Redis environment variables to `webapp.yaml`
- [ ] Update readiness probe to include Redis check
- [ ] Document configuration options

#### Phase 5: Testing
- [ ] Write unit tests for `RedisSessionStorage`
- [ ] Write integration tests with TestContainers
- [ ] Test session persistence across pod restarts
- [ ] Test concurrent session access from multiple replicas
- [ ] Load test with multiple WebApp replicas

#### Phase 6: Deployment
- [ ] Deploy updated WebApp image
- [ ] Verify Redis connectivity
- [ ] Monitor session creation/retrieval metrics
- [ ] Test login flow with multiple replicas

## Testing Strategy

### Unit Tests

**Location:** `webapp/src/test/kotlin/io/ktor/foodies/server/session/RedisSessionStorageSpec.kt`

```kotlin
package io.ktor.foodies.server.session

import com.redis.testcontainers.RedisContainer
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

@Testcontainers
@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisSessionStorageSpec {

    companion object {
        @Container
        val redis = RedisContainer("redis:7-alpine")

        lateinit var redisClient: RedisClient

        @BeforeAll
        @JvmStatic
        fun setup() {
            redisClient = RedisClient.create(redis.redisURI)
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            redisClient.shutdown()
        }
    }

    @Test
    fun `write and read session`() = runTest {
        val storage = RedisSessionStorage(redisClient.connect().coroutines())
        val session = UserSession(idToken = "test-token-123")

        storage.write("session-1", session)
        val retrieved = storage.read("session-1")

        assertNotNull(retrieved)
        assertEquals(session.idToken, retrieved.idToken)
    }

    @Test
    fun `read non-existent session returns null`() = runTest {
        val storage = RedisSessionStorage(redisClient.connect().coroutines())

        val retrieved = storage.read("non-existent")

        assertNull(retrieved)
    }

    @Test
    fun `invalidate removes session`() = runTest {
        val storage = RedisSessionStorage(redisClient.connect().coroutines())
        val session = UserSession(idToken = "test-token-456")

        storage.write("session-2", session)
        storage.invalidate("session-2")
        val retrieved = storage.read("session-2")

        assertNull(retrieved)
    }

    @Test
    fun `session expires after TTL`() = runTest {
        val storage = RedisSessionStorage(
            redis = redisClient.connect().coroutines(),
            ttlSeconds = 2
        )
        val session = UserSession(idToken = "test-token-789")

        storage.write("session-3", session)

        // Verify session exists
        assertNotNull(storage.read("session-3"))

        // Wait for expiration
        delay(3.seconds)

        // Session should be expired
        assertNull(storage.read("session-3"))
    }

    @Test
    fun `reading session refreshes TTL`() = runTest {
        val storage = RedisSessionStorage(
            redis = redisClient.connect().coroutines(),
            ttlSeconds = 3
        )
        val session = UserSession(idToken = "test-token-refresh")

        storage.write("session-4", session)

        // Read after 2 seconds (before expiration)
        delay(2.seconds)
        assertNotNull(storage.read("session-4"))

        // Wait another 2 seconds (total 4, but TTL was refreshed)
        delay(2.seconds)

        // Session should still exist because TTL was refreshed
        assertNotNull(storage.read("session-4"))
    }

    @Test
    fun `sessions are isolated by ID`() = runTest {
        val storage = RedisSessionStorage(redisClient.connect().coroutines())
        val session1 = UserSession(idToken = "token-user-1")
        val session2 = UserSession(idToken = "token-user-2")

        storage.write("user-1", session1)
        storage.write("user-2", session2)

        val retrieved1 = storage.read("user-1")
        val retrieved2 = storage.read("user-2")

        assertNotNull(retrieved1)
        assertNotNull(retrieved2)
        assertEquals("token-user-1", retrieved1.idToken)
        assertEquals("token-user-2", retrieved2.idToken)
    }

    @Test
    fun `overwriting session updates data`() = runTest {
        val storage = RedisSessionStorage(redisClient.connect().coroutines())
        val session1 = UserSession(idToken = "original-token")
        val session2 = UserSession(idToken = "updated-token")

        storage.write("session-5", session1)
        storage.write("session-5", session2)

        val retrieved = storage.read("session-5")

        assertNotNull(retrieved)
        assertEquals("updated-token", retrieved.idToken)
    }
}
```

### Integration Tests

**Location:** `webapp/src/test/kotlin/io/ktor/foodies/server/SessionIntegrationSpec.kt`

```kotlin
package io.ktor.foodies.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SessionIntegrationSpec {

    @Test
    fun `session persists across requests with Redis`() = testApplication {
        application {
            // Configure test application with Redis session storage
            // Using TestContainers Redis
        }

        // First request - create session
        val response1 = client.get("/login")
        val cookies1 = response1.headers.getAll(HttpHeaders.SetCookie)
        val sessionCookie = cookies1?.firstOrNull { it.contains("USER_SESSION") }
        assertNotNull(sessionCookie)

        // Second request - use session
        val response2 = client.get("/") {
            header(HttpHeaders.Cookie, sessionCookie)
        }
        assertEquals(HttpStatusCode.OK, response2.status)

        // Session data should be retrieved from Redis
    }

    @Test
    fun `logout invalidates session in Redis`() = testApplication {
        application {
            // Configure test application
        }

        // Login
        val loginResponse = client.get("/login")
        val sessionCookie = loginResponse.headers.getAll(HttpHeaders.SetCookie)
            ?.firstOrNull { it.contains("USER_SESSION") }
        assertNotNull(sessionCookie)

        // Logout
        client.get("/logout") {
            header(HttpHeaders.Cookie, sessionCookie)
        }

        // Session should no longer exist in Redis
        val homeResponse = client.get("/") {
            header(HttpHeaders.Cookie, sessionCookie)
        }
        // Should not have active session
    }
}
```

### Load Testing

Use K6 or similar tool to test:
1. Concurrent users with multiple WebApp replicas
2. Session persistence across pod failures
3. Redis connection pool performance
4. Session creation/read/invalidate latency

**Example K6 Script:**
```javascript
import http from 'k6/http';
import { check } from 'k6';

export let options = {
    vus: 100,
    duration: '5m',
};

export default function () {
    const loginRes = http.get('http://foodies.local:8080/login');
    check(loginRes, {
        'login successful': (r) => r.status === 200,
        'session cookie set': (r) => r.cookies['USER_SESSION'] !== undefined,
    });

    const sessionCookie = loginRes.cookies['USER_SESSION'];
    const homeRes = http.get('http://foodies.local:8080/', {
        cookies: { USER_SESSION: sessionCookie },
    });
    check(homeRes, {
        'session persisted': (r) => r.status === 200,
    });
}
```

## Performance Considerations

### Redis Connection Pooling
- Lettuce uses connection pooling by default
- Configure pool size based on expected load
- Monitor connection pool metrics

### Session Serialization
- JSON serialization is fast for small session objects
- Current `UserSession` is lightweight (single string)
- Consider MessagePack or Protobuf for larger session data

### Redis Operation Latency
- **Target:** < 5ms per session operation (read/write)
- Use Redis pipelining for batch operations if needed
- Monitor Redis slow log

### TTL Strategy
- **Sliding Window:** TTL refreshes on each read
- **Absolute Expiration:** Not implemented (could add separate field)
- Balance between security (short TTL) and UX (longer sessions)

### Memory Usage
- Each session: ~200 bytes (JSON-serialized)
- 10,000 concurrent users: ~2MB in Redis
- Redis memory limit: 256MB (k8s config) can handle 1M+ sessions

## Security Considerations

### Session ID Generation
- Use cryptographically secure random session IDs
- Minimum 128-bit entropy (16 bytes base64-encoded)
- Avoid predictable session IDs

```kotlin
import java.security.SecureRandom
import java.util.Base64

fun generateSessionId(): String {
    val random = SecureRandom()
    val bytes = ByteArray(24) // 192 bits
    random.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
```

### Cookie Security
- `HttpOnly`: Prevents JavaScript access to session cookie
- `Secure`: HTTPS-only in production
- `SameSite=lax`: CSRF protection
- Signed cookies: Ktor signs cookies by default

### Redis Security
- Redis password authentication in production
- Network isolation (Redis not exposed externally)
- TLS for Redis connections in production (optional)

### Session Data Encryption
- Consider encrypting session data at rest in Redis
- Not required if Redis is properly secured
- ID token can be encrypted if extra security needed

### Session Fixation Prevention
- Generate new session ID after login
- Invalidate old session on logout
- Rotate session IDs periodically

### Token Storage
- Store only the ID token, not sensitive user data
- ID token has limited lifespan (1 hour)
- Refresh tokens should NOT be stored in session

## Monitoring & Observability

### Metrics to Track
1. **Session Operations:**
   - Session creation rate
   - Session read rate
   - Session invalidation rate
   - Session expiration rate

2. **Redis Health:**
   - Connection pool utilization
   - Redis command latency (p50, p95, p99)
   - Redis memory usage
   - Redis connection errors

3. **Application Health:**
   - Session-related errors
   - Session not found rate
   - Redis timeout rate

### Logging
```kotlin
private val logger = LoggerFactory.getLogger(RedisSessionStorage::class.java)

override suspend fun read(id: String): UserSession? {
    return try {
        val data = redis.get(sessionKey(id))
        if (data == null) {
            logger.debug("Session not found: {}", id)
            return null
        }
        redis.expire(sessionKey(id), ttlSeconds)
        Json.decodeFromString<UserSession>(data)
    } catch (e: Exception) {
        logger.error("Failed to read session: {}", id, e)
        null
    }
}
```

### Alerts
- Redis connection failures
- High session operation latency (> 50ms)
- Redis memory usage > 80%
- Session not found rate > 5%

## Alternative Approaches Considered

### 1. Sticky Sessions (SessionAffinity)
**Pros:**
- No Redis dependency
- Lower latency (in-memory)
- Simpler implementation

**Cons:**
- Uneven load distribution
- Session loss on pod failure
- Complicates rolling deployments
- Not true horizontal scaling

**Decision:** Rejected in favor of Redis for true stateless architecture

### 2. JWT-Only (No Server Sessions)
**Pros:**
- Fully stateless
- No session storage needed
- Scales infinitely

**Cons:**
- Cannot invalidate tokens (logout is client-side only)
- Larger cookie size
- Token refresh complexity
- Security concerns with long-lived tokens

**Decision:** Rejected because logout requires server-side session invalidation

### 3. Database-Backed Sessions
**Pros:**
- Leverages existing Postgres infrastructure
- ACID guarantees

**Cons:**
- Higher latency than Redis
- More load on database
- Overkill for session storage

**Decision:** Rejected in favor of Redis (purpose-built for caching)

### 4. Memcached
**Pros:**
- Simple key-value store
- Fast

**Cons:**
- No persistence
- Less feature-rich than Redis
- No TTL refresh on read

**Decision:** Rejected because Redis is already in use by Basket service

## Future Enhancements

### 1. Session Clustering
- Redis Sentinel for high availability
- Redis Cluster for horizontal scaling
- Automatic failover

### 2. Session Analytics
- Track session duration
- User activity patterns
- Geographic distribution

### 3. Multi-Datacenter Sessions
- Redis replication across regions
- Geo-distributed session storage
- Conflict resolution

### 4. Session Enrichment
- Store user preferences
- Cache user profile data
- Reduce database lookups

### 5. Advanced Security
- Session anomaly detection
- Concurrent session limits
- Device fingerprinting

## References

- [Ktor Sessions Documentation](https://ktor.io/docs/sessions.html)
- [Lettuce Redis Client](https://lettuce.io/)
- [Redis Best Practices](https://redis.io/docs/manual/patterns/)
- [OWASP Session Management Cheat Sheet](https://cheatsheetsecurity.com/cheatsheets/session-management-cheat-sheet)
- [Basket Service Redis Implementation](fleet-file://mmglq7uf96d8i197ro8d/Users/simonvergauwen/Developer/foodies/basket/src/main/kotlin/io/ktor/foodies/basket/Repository.kt?type=file&root=%252F)

## Migration Checklist

### Development
- [ ] Create `SessionStorage` interface
- [ ] Implement `RedisSessionStorage`
- [ ] Implement `InMemorySessionStorage` for tests
- [ ] Add Redis configuration to `Config.kt`
- [ ] Update `Security.kt` to use Redis sessions
- [ ] Add Redis dependencies to `build.gradle.kts`
- [ ] Write unit tests with TestContainers
- [ ] Write integration tests
- [ ] Update `application.yaml` with Redis config

### Kubernetes
- [ ] Update `webapp.yaml` with Redis env vars
- [ ] Add Redis health check to readiness probe
- [ ] Verify Redis service connectivity
- [ ] Test deployment with 2+ replicas

### Testing
- [ ] Test login flow with multiple replicas
- [ ] Test session persistence across pod restarts
- [ ] Test logout invalidates session
- [ ] Load test with 100+ concurrent users
- [ ] Verify Redis connection pooling

### Monitoring
- [ ] Add session operation metrics
- [ ] Add Redis health check to Cohort
- [ ] Configure alerts for Redis failures
- [ ] Monitor session operation latency
- [ ] Track session not found rate

### Documentation
- [ ] Update deployment documentation
- [ ] Document Redis configuration options
- [ ] Update troubleshooting guide
- [ ] Document rollback procedure

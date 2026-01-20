# Design strongly-typed authentication DSL

## Problem
Current authentication code exposes low-level JWT details:
```kotlin
// Current: Raw JWT access
val buyerId = call.principal<JWTPrincipal>()!!.buyerId()
val azp = credential.payload.getClaim("azp")?.asString()

// Current: Context receivers with manual extraction
context(ServerSessionScope)
suspend fun serviceCredentials(): ServiceCredentials
```

Problems:
- Business logic knows about JWT structure
- Manual claim extraction error-prone
- No type safety for permissions/scopes
- Requires !! or null checks everywhere
- Context receivers expose implementation details

## Goals
Design a clean DSL that:
1. **Hides JWT/OAuth implementation details**
2. **Provides strongly-typed principals**
3. **Type-safe permission checking**
4. **Clear routing DSL**
5. **Zero null checks in business logic**

## Proposed API Design

### Principals
```kotlin
// Strongly-typed principals (no JWT exposure)
sealed interface AuthPrincipal

data class UserPrincipal(
    val userId: String,
    val email: String?,
    val name: String?,
    val roles: Set<String>,
    val scopes: Set<String>
) : AuthPrincipal

data class ServicePrincipal(
    val serviceId: String,
    val scopes: Set<String>
) : AuthPrincipal
```

### Routing DSL
```kotlin
// User-authenticated routes
fun Route.authenticatedUser(build: Route.() -> Unit)

// Service-authenticated routes
fun Route.authenticatedService(build: Route.() -> Unit)

// Combined (either user or service)
fun Route.authenticated(build: Route.() -> Unit)
```

### Principal Access
```kotlin
// In route handlers
suspend fun RoutingContext.userPrincipal(): UserPrincipal
suspend fun RoutingContext.servicePrincipal(): ServicePrincipal
suspend fun RoutingContext.authPrincipal(): AuthPrincipal

// Usage
get("/basket") {
    val user = userPrincipal()
    basketService.getBasket(user.userId)
}
```

### Permission Checking
```kotlin
// Declarative permission checks
fun Route.requireScope(scope: String, build: Route.() -> Unit)
fun Route.requireRole(role: String, build: Route.() -> Unit)

// Programmatic checks
suspend fun RoutingContext.hasScope(scope: String): Boolean
suspend fun RoutingContext.hasRole(role: String): Boolean
suspend fun AuthPrincipal.requireScope(scope: String)

// Usage
authenticatedUser {
    requireScope("basket:write") {
        post("/basket/items") { /* ... */ }
    }
}
```

### Client-side Token Propagation
```kotlin
// For service clients (like BasketClient in Order service)
fun HttpClient.withUserAuth(): HttpClient
fun HttpClient.withServiceAuth(config: ServiceClientConfig): HttpClient

// Automatic selection
fun HttpClient.withAuth(): HttpClient // Detects context
```

## Example Usage

### Basket Service Routes
```kotlin
fun Route.basketRoutes(basketService: BasketService) {
    // User routes
    authenticatedUser {
        route("/basket") {
            get {
                val user = userPrincipal()
                call.respond(basketService.getBasket(user.userId))
            }

            requireScope("basket:write") {
                post("/items") {
                    val user = userPrincipal()
                    val request = call.receive<AddItemRequest>()
                    call.respond(basketService.addItem(user.userId, request))
                }
            }
        }
    }

    // Service routes
    authenticatedService {
        get("/internal/basket/{userId}") {
            val service = servicePrincipal()
            val userId by call.parameters
            logger.info("Service {} accessing basket", service.serviceId)
            call.respond(basketService.getBasket(userId))
        }
    }
}
```

### Order Service Client
```kotlin
class OrderService(
    private val basketClient: BasketClient,
    // ...
) {
    suspend fun placeOrder(userId: String) {
        // Automatically uses user token if in user context,
        // or service token if in background job context
        val basket = basketClient.getBasket(userId)
        // ...
    }
}
```

## Design Considerations

### 1. Context Detection
How to know if we're in user vs service context?
- Option A: Explicit client creation per context
- Option B: Context propagation (coroutine context)
- Option C: Dual HTTP clients (user + service)

### 2. Token Propagation
How to pass user tokens to downstream services?
- Option A: Like eShop - HttpClientAuthorizationDelegatingHandler
- Option B: Coroutine context propagation
- Option C: Explicit token passing

### 3. Error Handling
Unified error responses:
- 401 Unauthorized (missing/invalid token)
- 403 Forbidden (insufficient permissions)
- Custom error messages without leaking security details

### 4. Testing Support
Test DSL for creating principals:
```kotlin
fun testUserPrincipal(
    userId: String = "test-user",
    roles: Set<String> = emptySet(),
    scopes: Set<String> = emptySet()
): UserPrincipal

fun testServicePrincipal(
    serviceId: String = "test-service",
    scopes: Set<String> = emptySet()
): ServicePrincipal
```

## Complete API Specification

### Core Types

```kotlin
// server-shared/src/main/kotlin/io/ktor/foodies/server/openid/Principals.kt

package io.ktor.foodies.server.openid

import io.ktor.server.auth.Principal

/**
 * Base interface for all authentication principals in the system.
 * Sealed hierarchy ensures type-safe exhaustive handling.
 */
sealed interface AuthPrincipal : Principal

/**
 * Principal representing an authenticated end user.
 * Derived from OpenID Connect ID token or access token.
 */
data class UserPrincipal(
    /** Unique user identifier from JWT 'sub' claim */
    val userId: String,
    /** User's email address from 'email' claim (optional) */
    val email: String? = null,
    /** User's display name from 'name' or 'preferred_username' claim (optional) */
    val name: String? = null,
    /** User roles from 'realm_access.roles' claim */
    val roles: Set<String> = emptySet(),
    /** OAuth2 scopes from 'scope' claim (space-separated string parsed to set) */
    val scopes: Set<String> = emptySet()
) : AuthPrincipal {
    /** Check if user has a specific role */
    fun hasRole(role: String): Boolean = role in roles

    /** Check if user has a specific scope */
    fun hasScope(scope: String): Boolean = scope in scopes

    /** Check if user has all of the specified scopes */
    fun hasAllScopes(vararg scopes: String): Boolean = scopes.all { it in this.scopes }

    /** Check if user has any of the specified scopes */
    fun hasAnyScope(vararg scopes: String): Boolean = scopes.any { it in this.scopes }
}

/**
 * Principal representing an authenticated service using client credentials flow.
 * Replaces ServiceCredentials with consistent naming and stronger typing.
 */
data class ServicePrincipal(
    /** Service identifier from JWT 'azp' (authorized party) claim */
    val serviceId: String,
    /** OAuth2 scopes from 'scope' claim */
    val scopes: Set<String> = emptySet()
) : AuthPrincipal {
    /** Check if service has a specific scope */
    fun hasScope(scope: String): Boolean = scope in scopes

    /** Check if service has all of the specified scopes */
    fun hasAllScopes(vararg scopes: String): Boolean = scopes.all { it in this.scopes }
}
```

### Routing DSL Extensions

```kotlin
// server-shared/src/main/kotlin/io/ktor/foodies/server/openid/RoutingDsl.kt

package io.ktor.foodies.server.openid

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext

/**
 * Creates a route that requires user authentication (AUTH_USER).
 * All nested routes will require a valid user token.
 *
 * @throws UnauthorizedException if user token is missing or invalid
 */
fun Route.authenticatedUser(build: Route.() -> Unit): Route =
    authenticate(AUTH_USER) { build() }

/**
 * Creates a route that requires service authentication (AUTH_SERVICE).
 * All nested routes will require a valid service client credentials token.
 *
 * @throws UnauthorizedException if service token is missing or invalid
 */
fun Route.authenticatedService(build: Route.() -> Unit): Route =
    authenticate(AUTH_SERVICE) { build() }

/**
 * Creates a route that accepts either user or service authentication.
 * Use when an endpoint can be called by both users and services.
 *
 * @throws UnauthorizedException if no valid token is present
 */
fun Route.authenticated(build: Route.() -> Unit): Route =
    authenticate(AUTH_USER, AUTH_SERVICE, optional = false) { build() }

/**
 * Extracts the UserPrincipal from the current request.
 * Only use inside authenticatedUser { } blocks.
 *
 * @return UserPrincipal for the authenticated user
 * @throws UnauthorizedException if no user principal is present
 */
suspend fun RoutingContext.userPrincipal(): UserPrincipal =
    call.principal<UserPrincipal>()
        ?: throw UnauthorizedException("User authentication required")

/**
 * Extracts the ServicePrincipal from the current request.
 * Only use inside authenticatedService { } blocks.
 *
 * @return ServicePrincipal for the authenticated service
 * @throws UnauthorizedException if no service principal is present
 */
suspend fun RoutingContext.servicePrincipal(): ServicePrincipal =
    call.principal<ServicePrincipal>()
        ?: throw UnauthorizedException("Service authentication required")

/**
 * Extracts any AuthPrincipal (user or service) from the current request.
 * Use inside authenticated { } blocks when you need to handle both types.
 *
 * @return AuthPrincipal (either UserPrincipal or ServicePrincipal)
 * @throws UnauthorizedException if no principal is present
 */
suspend fun RoutingContext.authPrincipal(): AuthPrincipal =
    call.principal<UserPrincipal>()
        ?: call.principal<ServicePrincipal>()
        ?: throw UnauthorizedException("Authentication required")

/** Custom exception for authentication failures */
class UnauthorizedException(message: String) : Exception(message)
```

### Permission Checking DSL

```kotlin
// server-shared/src/main/kotlin/io/ktor/foodies/server/openid/PermissionDsl.kt

package io.ktor.foodies.server.openid

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.createRouteFromPath
import io.ktor.util.pipeline.PipelineInterceptor

/**
 * Creates a nested route that requires a specific OAuth2 scope.
 * Returns 403 Forbidden if the authenticated principal lacks the scope.
 *
 * @param scope Required OAuth2 scope (e.g., "basket:write")
 */
fun Route.requireScope(scope: String, build: Route.() -> Unit): Route {
    return createChild(object : RouteSelector() {
        override fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
            RouteSelectorEvaluation.Constant
    }).apply {
        intercept(ApplicationCallPipeline.Call) {
            val principal = call.authPrincipal()
            if (!principal.hasScope(scope)) {
                call.respond(HttpStatusCode.Forbidden, "Missing required scope: $scope")
                finish()
            }
        }
        build()
    }
}

/**
 * Creates a nested route that requires a specific user role.
 * Returns 403 Forbidden if the user lacks the role.
 * Only works with UserPrincipal (not ServicePrincipal).
 *
 * @param role Required role (e.g., "admin")
 */
fun Route.requireRole(role: String, build: Route.() -> Unit): Route {
    return createChild(object : RouteSelector() {
        override fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
            RouteSelectorEvaluation.Constant
    }).apply {
        intercept(ApplicationCallPipeline.Call) {
            val principal = call.principal<UserPrincipal>()
            if (principal == null || !principal.hasRole(role)) {
                call.respond(HttpStatusCode.Forbidden, "Missing required role: $role")
                finish()
            }
        }
        build()
    }
}

/**
 * Creates a nested route that requires multiple scopes (AND logic).
 *
 * @param scopes All required scopes
 */
fun Route.requireAllScopes(vararg scopes: String, build: Route.() -> Unit): Route {
    return createChild(object : RouteSelector() {
        override fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
            RouteSelectorEvaluation.Constant
    }).apply {
        intercept(ApplicationCallPipeline.Call) {
            val principal = call.authPrincipal()
            val missing = scopes.filter { !principal.hasScope(it) }
            if (missing.isNotEmpty()) {
                call.respond(HttpStatusCode.Forbidden, "Missing required scopes: ${missing.joinToString()}")
                finish()
            }
        }
        build()
    }
}

/** Check if principal has a specific scope (programmatic check) */
suspend fun RoutingContext.hasScope(scope: String): Boolean =
    authPrincipal().hasScope(scope)

/** Check if user has a specific role (programmatic check) */
suspend fun RoutingContext.hasRole(role: String): Boolean =
    (call.principal<UserPrincipal>())?.hasRole(role) ?: false

/** Require scope or throw ForbiddenException */
fun AuthPrincipal.requireScope(scope: String) {
    if (!hasScope(scope)) {
        throw ForbiddenException("Missing required scope: $scope")
    }
}

/** Require role or throw ForbiddenException */
fun UserPrincipal.requireRole(role: String) {
    if (!hasRole(role)) {
        throw ForbiddenException("Missing required role: $role")
    }
}

/** Custom exception for authorization failures */
class ForbiddenException(message: String) : Exception(message)

// Helper extension for any AuthPrincipal
private fun AuthPrincipal.hasScope(scope: String): Boolean = when (this) {
    is UserPrincipal -> hasScope(scope)
    is ServicePrincipal -> hasScope(scope)
}
```

### Client-Side Token Propagation

```kotlin
// server-shared/src/main/kotlin/io/ktor/foodies/server/openid/ClientAuth.kt

package io.ktor.foodies.server.openid

import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine context element to propagate authentication tokens.
 * Similar to MDC for logging, but for authentication.
 */
data class AuthContext(
    val userToken: String? = null,
    val serviceToken: String? = null
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<AuthContext>
    override val key: CoroutineContext.Key<*> get() = Key
}

/**
 * Creates an HttpClient that automatically propagates user tokens.
 * Extracts the Authorization header from the current request context
 * and forwards it to downstream service calls.
 *
 * Use for service-to-service calls that should maintain user identity.
 *
 * @return HttpClient configured with user token propagation
 */
fun HttpClient.withUserAuth(): HttpClient {
    return config {
        install(createClientPlugin("UserAuthPlugin") {
            onRequest { request, _ ->
                // Try to get token from coroutine context
                val authContext = currentCoroutineContext()[AuthContext]
                val token = authContext?.userToken

                if (token != null) {
                    request.header(HttpHeaders.Authorization, "Bearer $token")
                }
            }
        })
    }
}

/**
 * Creates an HttpClient that uses service client credentials.
 * Obtains a service token using client credentials flow and
 * adds it to all outbound requests.
 *
 * Use for background jobs or service-to-service calls without user context.
 *
 * @param tokenClient Client for obtaining service tokens
 * @return HttpClient configured with service authentication
 */
fun HttpClient.withServiceAuth(tokenClient: ServiceTokenClient): HttpClient {
    return config {
        install(createClientPlugin("ServiceAuthPlugin") {
            onRequest { request, _ ->
                // Get service token (cached by ServiceTokenClient)
                val token = tokenClient.getAccessToken()
                request.header(HttpHeaders.Authorization, "Bearer $token")
            }
        })
    }
}

/**
 * Smart client that automatically selects user or service auth based on context.
 * Checks coroutine context for user token first, falls back to service token.
 *
 * Use when you want automatic selection based on calling context.
 *
 * @param tokenClient Client for service tokens (fallback)
 * @return HttpClient with automatic auth selection
 */
fun HttpClient.withAuth(tokenClient: ServiceTokenClient): HttpClient {
    return config {
        install(createClientPlugin("AutoAuthPlugin") {
            onRequest { request, _ ->
                val authContext = currentCoroutineContext()[AuthContext]
                val token = authContext?.userToken ?: tokenClient.getAccessToken()
                request.header(HttpHeaders.Authorization, "Bearer $token")
            }
        })
    }
}

/**
 * Extension to extract and propagate authentication from ApplicationCall.
 * Use in route handlers before calling downstream services.
 */
suspend fun <R> ApplicationCall.withAuthContext(block: suspend () -> R): R {
    val token = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
    return kotlinx.coroutines.withContext(AuthContext(userToken = token)) {
        block()
    }
}
```

### Testing Support

```kotlin
// server-shared-test/src/main/kotlin/io/ktor/foodies/server/openid/TestPrincipals.kt

package io.ktor.foodies.server.openid

import io.ktor.server.auth.Principal
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationRequest

/**
 * Creates a test UserPrincipal with sensible defaults.
 * All parameters optional for convenience.
 */
fun testUserPrincipal(
    userId: String = "test-user-${randomId()}",
    email: String? = "test@example.com",
    name: String? = "Test User",
    roles: Set<String> = emptySet(),
    scopes: Set<String> = setOf("openid", "profile", "email")
): UserPrincipal = UserPrincipal(
    userId = userId,
    email = email,
    name = name,
    roles = roles,
    scopes = scopes
)

/**
 * Creates a test ServicePrincipal with sensible defaults.
 */
fun testServicePrincipal(
    serviceId: String = "test-service-${randomId()}",
    scopes: Set<String> = setOf("service:read", "service:write")
): ServicePrincipal = ServicePrincipal(
    serviceId = serviceId,
    scopes = scopes
)

/**
 * Sets a principal in test application context.
 * Use with testApplication { } blocks.
 */
fun TestApplicationRequest.withPrincipal(principal: Principal) {
    // Implementation depends on test framework
    // This will be in ApplicationTestBuilder context
}

/**
 * Creates a test with authenticated user principal.
 */
fun ApplicationTestBuilder.authenticatedUserTest(
    principal: UserPrincipal = testUserPrincipal(),
    block: suspend TestApplicationCall.() -> Unit
) {
    // Helper for setting up authenticated test calls
}

/**
 * Creates a test with authenticated service principal.
 */
fun ApplicationTestBuilder.authenticatedServiceTest(
    principal: ServicePrincipal = testServicePrincipal(),
    block: suspend TestApplicationCall.() -> Unit
) {
    // Helper for setting up authenticated test calls
}

private fun randomId() = java.util.UUID.randomUUID().toString().take(8)
```

## Migration Guide

### Step 1: Replace ServiceCredentials with ServicePrincipal

**Before:**
```kotlin
// Security.kt
data class ServiceCredentials(val clientId: String, val scopes: List<String>)

// Routes.kt
val servicePrincipal = call.principal<ServiceCredentials>()!!
logger.info("Service {} accessing basket", servicePrincipal.clientId)
```

**After:**
```kotlin
// Principals.kt - Already defined
data class ServicePrincipal(val serviceId: String, val scopes: Set<String>)

// Routes.kt
val service = servicePrincipal() // Extension function, no null checks
logger.info("Service {} accessing basket", service.serviceId)
```

**Changes Required:**
1. Update JWT validation in `Security.kt` to create `ServicePrincipal` instead of `ServiceCredentials`
2. Change `clientId` to `serviceId`
3. Change `scopes: List<String>` to `scopes: Set<String>`
4. Remove `withServiceScope` context receiver pattern
5. Update all call sites in basket and order services

### Step 2: Add UserPrincipal and Validation

**Before:**
```kotlin
// Routes.kt - Manual JWT extraction
authenticate(AUTH_USER) {
    get("/basket") {
        val buyerId = call.principal<JWTPrincipal>()!!.buyerId()
        // ...
    }
}

private fun JWTPrincipal.buyerId(): String =
    payload.subject ?: throw IllegalStateException("JWT subject claim is missing")
```

**After:**
```kotlin
// Routes.kt - Clean DSL
authenticatedUser {
    get("/basket") {
        val user = userPrincipal()
        basketService.getBasket(user.userId)
        // ...
    }
}
```

**Implementation Required:**
```kotlin
// Security.kt - Add user JWT validation
jwt(AUTH_USER) {
    verifier(config.jwks(), config.issuer) {
        withAudience(auth.userAudience) // New config needed
    }
    validate { credential ->
        val sub = credential.payload.subject
        if (sub != null) {
            UserPrincipal(
                userId = sub,
                email = credential.payload.getClaim("email")?.asString(),
                name = credential.payload.getClaim("name")?.asString()
                    ?: credential.payload.getClaim("preferred_username")?.asString(),
                roles = credential.payload.getClaim("realm_access")
                    ?.asMap()?.get("roles") as? List<String>)?.toSet() ?: emptySet(),
                scopes = credential.payload.getClaim("scope")?.asString()
                    ?.split(" ")?.toSet() ?: emptySet()
            )
        } else {
            null
        }
    }
}
```

### Step 3: Migrate to Routing DSL

**Before:**
```kotlin
fun Route.basketUserRoutes(basketService: BasketService) = authenticate(AUTH_USER) {
    route("/basket") {
        get {
            val buyerId = call.principal<JWTPrincipal>()!!.buyerId()
            val basket = basketService.getBasket(buyerId)
            call.respond(basket)
        }
    }
}

fun Route.basketServiceRoutes(basketService: BasketService) = authenticate(AUTH_SERVICE) {
    get("/internal/basket/{userId}") {
        val servicePrincipal = call.principal<ServiceCredentials>()!!
        val userId: String by call.parameters
        // ...
    }
}
```

**After:**
```kotlin
fun Route.basketRoutes(basketService: BasketService) {
    // User routes - clean and type-safe
    authenticatedUser {
        route("/basket") {
            get {
                val user = userPrincipal()
                call.respond(basketService.getBasket(user.userId))
            }

            delete {
                val user = userPrincipal()
                basketService.clearBasket(user.userId)
                call.respond(HttpStatusCode.NoContent)
            }

            route("/items") {
                post {
                    val user = userPrincipal()
                    val request = call.receive<AddItemRequest>()
                    val validated = validate { request.validate() }
                    val basket = basketService.addItem(user.userId, validated)
                    if (basket == null) call.respond(HttpStatusCode.NotFound)
                    else call.respond(basket)
                }
            }
        }
    }

    // Service routes - equally clean
    authenticatedService {
        get("/internal/basket/{userId}") {
            val service = servicePrincipal()
            val userId: String by call.parameters

            logger.info("Service {} accessing basket for user {}", service.serviceId, userId)
            call.respond(basketService.getBasket(userId))
        }
    }
}
```

### Step 4: Implement Client Token Propagation

**Before:**
```kotlin
// HttpBasketClient.kt - No authentication
class HttpBasketClient(
    private val httpClient: HttpClient,
    private val baseUrl: String
) : BasketClient {
    override suspend fun getBasket(buyerId: String): CustomerBasket? {
        return httpClient.get("$basketBaseUrl/internal/basket/$buyerId")
            .body<CustomerBasket>()
    }
}
```

**After:**
```kotlin
// HttpBasketClient.kt - With auth support
class HttpBasketClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val authMode: AuthMode
) : BasketClient {
    override suspend fun getBasket(buyerId: String): CustomerBasket? {
        return httpClient.get("$basketBaseUrl/internal/basket/$buyerId")
            .body<CustomerBasket>()
    }
}

enum class AuthMode { USER, SERVICE, AUTO }

// Module configuration
fun Application.basketClientModule() {
    val serviceTokenClient = ServiceTokenClient(/* ... */)

    // For user requests (propagate user token)
    val userBasketClient = HttpBasketClient(
        httpClient = httpClient.withUserAuth(),
        baseUrl = config.basketUrl,
        authMode = AuthMode.USER
    )

    // For background jobs (use service token)
    val serviceBasketClient = HttpBasketClient(
        httpClient = httpClient.withServiceAuth(serviceTokenClient),
        baseUrl = config.basketUrl,
        authMode = AuthMode.SERVICE
    )
}

// In route handler - propagate user context
authenticatedUser {
    post("/orders") {
        val user = userPrincipal()

        // User token automatically propagated to basket service
        call.withAuthContext {
            val basket = basketClient.getBasket(user.userId)
            // ...
        }
    }
}
```

### Step 5: Add Permission Checks

**New Capability - Scope-based Authorization:**
```kotlin
authenticatedUser {
    route("/basket") {
        // Read operations - no special scope needed
        get { /* ... */ }

        // Write operations - require explicit scope
        requireScope("basket:write") {
            post("/items") { /* ... */ }
            put("/items/{id}") { /* ... */ }
            delete("/items/{id}") { /* ... */ }
        }

        // Admin-only operation
        requireRole("admin") {
            delete("/all") { /* ... */ }
        }
    }
}
```

## Configuration Changes

### application.yaml Updates

```yaml
# Before
auth:
  issuer: "https://keycloak.foodies.local/realms/foodies"
  service_audience: "service-mesh"

# After
auth:
  issuer: "https://keycloak.foodies.local/realms/foodies"
  service_audience: "service-mesh"
  user_audience: "account"  # New: for user token validation
```

### Gradle Dependencies

```kotlin
// server-shared/build.gradle.kts
dependencies {
    // Existing
    api(libs.ktor.server.auth)
    api(libs.ktor.server.auth.jwt)

    // New for client auth
    api(libs.ktor.client.core)
    api(libs.ktor.client.auth)
}

// server-shared-test/build.gradle.kts
dependencies {
    api(projects.serverShared)
    api(libs.ktor.server.test.host)
    api(libs.kotlin.test)
}
```

## Testing Strategy

### Unit Tests
```kotlin
class UserPrincipalTest {
    @Test
    fun `hasScope returns true for existing scope`() {
        val principal = testUserPrincipal(scopes = setOf("read", "write"))
        assertTrue(principal.hasScope("read"))
        assertFalse(principal.hasScope("admin"))
    }

    @Test
    fun `hasAllScopes checks all scopes`() {
        val principal = testUserPrincipal(scopes = setOf("read", "write", "delete"))
        assertTrue(principal.hasAllScopes("read", "write"))
        assertFalse(principal.hasAllScopes("read", "admin"))
    }
}
```

### Integration Tests
```kotlin
class BasketRoutesTest {
    @Test
    fun `authenticated user can access basket`() = testApplication {
        val user = testUserPrincipal(userId = "user-123")

        authenticatedUserTest(user) {
            val response = client.get("/basket")
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun `unauthenticated request returns 401`() = testApplication {
        val response = client.get("/basket")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `service can access internal basket endpoint`() = testApplication {
        val service = testServicePrincipal(serviceId = "order-service")

        authenticatedServiceTest(service) {
            val response = client.get("/internal/basket/user-123")
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }
}
```

## Implementation Order (Blocked Issues)

Based on dependencies, implement in this order:

1. **bd-33s**: Implement UserPrincipal and AUTH_USER validation
   - Add UserPrincipal data class
   - Update Security.kt JWT validation
   - Add unit tests

2. **bd-7fi**: Refactor ServicePrincipal to match DSL design
   - Rename ServiceCredentials → ServicePrincipal
   - Change clientId → serviceId
   - Update JWT validation
   - Remove context receiver pattern

3. **bd-33p**: Implement routing DSL (authenticatedUser, authenticatedService)
   - Add RoutingDsl.kt with extension functions
   - Add PermissionDsl.kt with scope/role checking
   - Add integration tests

4. **bd-2uo**: Implement user token propagation for HTTP clients
   - Add ClientAuth.kt with HttpClient extensions
   - Add AuthContext coroutine element
   - Add withAuthContext helper
   - Test token propagation

5. **bd-1xh**: Implement hybrid BasketClient (user + service tokens)
   - Update HttpBasketClient with auth modes
   - Configure dual clients in order service
   - Test both auth flows

## Decision Log

### Context Detection (Design Consideration #1)
**Decision**: Use coroutine context propagation (Option B)
**Rationale**:
- Most Kotlin-idiomatic approach
- Transparent to business logic
- Works with suspend functions naturally
- Similar to SLF4J MDC pattern developers know

### Token Propagation (Design Consideration #2)
**Decision**: Hybrid of Options A and B
**Rationale**:
- Use AuthContext coroutine element for user tokens (like MDC)
- Use ServiceTokenClient for service tokens (explicit, cached)
- Provides flexibility for both patterns

### Error Handling (Design Consideration #3)
**Approach**: Structured exceptions with consistent HTTP status codes
```kotlin
UnauthorizedException → 401 Unauthorized
ForbiddenException → 403 Forbidden
```
- Never expose JWT validation details
- Provide clear error messages for debugging
- Log security events without sensitive data

### Testing Support (Design Consideration #4)
**Approach**: Separate test module with builders
- Keep test helpers separate from production code
- Provide sensible defaults for quick testing
- Allow full customization when needed
- Mirror production types exactly

## Deliverables
- [x] Complete API design document
- [x] Interface definitions with KDoc
- [x] Usage examples for common scenarios
- [x] Migration guide from current code
- [x] Test helper design
- [ ] Implementation (blocked issues)
- [ ] Team review and approval

## Next Steps
After design approval:
1. Create tasks in br for each implementation phase
2. Implement in dependency order (see Implementation Order)
3. Migrate existing services incrementally
4. Add comprehensive integration tests
5. Update documentation

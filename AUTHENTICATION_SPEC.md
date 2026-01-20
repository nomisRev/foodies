# Authentication Specification for Foodies Microservices

**Issue**: bd-2g4
**Version**: 1.0
**Date**: 2026-01-20
**Status**: Draft

## Executive Summary

This specification defines authentication and authorization patterns for the Foodies microservices architecture, addressing both user authentication flows and service-to-service (S2S) authentication. The specification follows industry best practices for zero-trust microservices architecture while building on the existing Keycloak-based authentication infrastructure.

## Table of Contents

1. [Current State Analysis](#1-current-state-analysis)
2. [Architecture Principles](#2-architecture-principles)
3. [User Authentication Flow](#3-user-authentication-flow)
4. [Service-to-Service Authentication](#4-service-to-service-authentication)
5. [Authorization Model](#5-authorization-model)
6. [Security Mechanisms](#6-security-mechanisms)
7. [Implementation Requirements](#7-implementation-requirements)
8. [Migration Strategy](#8-migration-strategy)
9. [Operational Considerations](#9-operational-considerations)
10. [Appendices](#10-appendices)

---

## 1. Current State Analysis

### 1.1 Existing Architecture

**Identity Provider**: Keycloak (Port 8000)
- OpenID Connect/OAuth2 provider
- Issuer: `http://localhost:8000/realms/foodies-keycloak`
- Manages user identities, roles, and credentials

**Services**:
- WebApp (8080): User-facing application with session-based auth
- Menu Service (8082): Product catalog
- Basket Service (8083): Shopping cart management
- Order Service (8084): Order processing and orchestration
- Profile Service (8081): User profile management
- Payment Service (8085): Payment processing

### 1.2 Current Authentication Patterns

**User Authentication**:
- OAuth2 Authorization Code flow with Keycloak
- Session storage in Redis (HTTP-only, secure cookies)
- Session contains: `idToken`, `accessToken`, `refreshToken`, `expiresIn`
- JWT validation using JWKS from Keycloak

**Service-to-Service Communication**:
- User JWT token propagation via `Authorization: Bearer {token}` header
- Services validate JWT audience claim (`foodies`)
- No dedicated service authentication mechanism
- Services act with delegated user permissions

### 1.3 Identified Gaps

1. **No Service Identity**: Services cannot authenticate themselves
2. **No Service Authorization**: No mechanism to verify caller service identity
3. **Token Propagation Coupling**: All services implicitly trust forwarded tokens
4. **Missing Service Impersonation Protection**: Any service can call any other service
5. **No Context Preservation**: User context lost in async/event-driven flows
6. **Inconsistent Authorization**: Only Order service implements role-based access

---

## 2. Architecture Principles

### 2.1 Zero Trust Architecture

**Principle**: Never trust, always verify
- Every service-to-service call must be authenticated
- Authorization decisions made at every boundary
- No implicit trust based on network location

### 2.2 Defense in Depth

**Layered Security**:
1. Network security (Kubernetes network policies)
2. Transport security (mTLS)
3. Application-level authentication (JWT)
4. Fine-grained authorization (RBAC/ABAC)

### 2.3 Principle of Least Privilege

**Access Control**:
- Services granted minimum permissions required
- User permissions distinct from service permissions
- Time-limited credentials with rotation

### 2.4 Security by Default

**Configuration**:
- Secure defaults in all environments
- Explicit opt-out for security features
- Fail-closed on authentication/authorization failures

---

## 3. User Authentication Flow

### 3.1 Authentication Protocol

**OAuth2 Authorization Code Flow with PKCE**

```
User Browser → WebApp → Keycloak
    │              │         │
    │─────(1)─────→│         │
    │              │─(2)────→│
    │←────(3)──────┴─────────┘
    │─────(4)─────→│         │
    │              │─(5)────→│
    │              │←(6)─────┘
    │←────(7)──────┘
```

**Flow Steps**:
1. User accesses protected resource
2. WebApp redirects to Keycloak authorization endpoint (with PKCE code_challenge)
3. User authenticates with Keycloak
4. Keycloak redirects to WebApp callback with authorization code
5. WebApp exchanges code for tokens (with PKCE code_verifier)
6. Keycloak returns `access_token`, `id_token`, `refresh_token`
7. WebApp creates session and returns session cookie

### 3.2 Session Management

**Session Storage**: Redis
- **Key Format**: `/session/{sessionId}`
- **TTL**: 3600 seconds (configurable)
- **Content**: Encrypted session data containing tokens

**Session Data Structure**:
```kotlin
data class UserSession(
    val idToken: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val issuedAt: Long = Clock.System.now().epochSeconds
)
```

**Cookie Configuration**:
- `HttpOnly`: true (prevent XSS)
- `Secure`: true (HTTPS only in production)
- `SameSite`: Lax (prevent CSRF)
- `Path`: /
- `MaxAge`: Session TTL

### 3.3 Token Refresh Strategy

**Proactive Refresh**:
- Refresh access token when < 5 minutes remaining
- Refresh on session access if expired
- Update session atomically in Redis

**Implementation**:
```kotlin
suspend fun refreshTokenIfNeeded(session: UserSession): UserSession {
    val now = Clock.System.now().epochSeconds
    val expiresAt = session.issuedAt + session.expiresIn
    val timeRemaining = expiresAt - now

    return if (timeRemaining < 300) { // < 5 minutes
        val newTokens = exchangeRefreshToken(session.refreshToken)
        session.copy(
            accessToken = newTokens.accessToken,
            expiresIn = newTokens.expiresIn,
            issuedAt = now
        )
    } else session
}
```

### 3.4 Logout Flow

**Logout Steps**:
1. WebApp deletes session from Redis
2. WebApp clears session cookie
3. WebApp redirects to Keycloak `end_session_endpoint`
4. Keycloak invalidates tokens
5. Keycloak redirects to post-logout URI

---

## 4. Service-to-Service Authentication

### 4.1 Authentication Strategy

**Dual-Token Pattern**: Separate user context from service identity

```
User Token (JWT):
- Subject: User ID
- Audience: foodies
- Roles: User roles
- Issued by: Keycloak

Service Token (JWT):
- Subject: Service principal (e.g., service:webapp)
- Audience: Target service (e.g., service:basket)
- Scope: Service permissions
- Issued by: Internal token service or Keycloak client credentials
```

### 4.2 Client Credentials Flow (Service Authentication)

**Protocol**: OAuth2 Client Credentials Grant

```
Service A → Token Service
    │              │
    │─────(1)─────→│ (client_id, client_secret, scope, audience)
    │←────(2)──────┘ (service_access_token)
    │
    ├─────(3)─────→ Service B (Authorization: Bearer {service_token})
    │                          (X-User-Token: {user_token})
    │←────(4)──────┘
```

**Implementation**:
1. Service authenticates with Keycloak using client credentials
2. Receives service-scoped access token
3. Includes service token in `Authorization` header
4. Optionally propagates user context in separate header

### 4.3 Service Registration

**Service Principals in Keycloak**:
- Each service registered as confidential client
- Client ID format: `service-{service-name}` (e.g., `service-webapp`)
- Client credentials: ID + Secret (from Kubernetes secrets)
- Service roles: Define service capabilities

**Example Service Client**:
```yaml
# Keycloak Client Configuration
clientId: service-webapp
protocol: openid-connect
publicClient: false
standardFlowEnabled: false
directAccessGrantsEnabled: false
serviceAccountsEnabled: true
authorizationServicesEnabled: true
defaultClientScopes:
  - email
  - profile
  - service:basket
  - service:menu
  - service:order
```

### 4.4 Token Structure

**Service Access Token (JWT)**:
```json
{
  "iss": "http://keycloak:8000/realms/foodies-keycloak",
  "sub": "service:webapp",
  "aud": "service:basket",
  "exp": 1737392000,
  "iat": 1737391700,
  "azp": "service-webapp",
  "scope": "basket:read basket:write",
  "client_id": "service-webapp",
  "typ": "Bearer"
}
```

### 4.5 User Context Propagation

**Custom Header Pattern**:
- `Authorization: Bearer {service_token}` - Service identity
- `X-User-Context: {user_token}` - User identity (optional)

**AuthContext Enhancement**:
```kotlin
sealed interface AuthContext : AbstractCoroutineContextElement {
    companion object Key : CoroutineContext.Key<AuthContext>

    override val key: CoroutineContext.Key<*> get() = AuthContext

    data class UserAuth(
        val accessToken: String
    ) : AuthContext

    data class ServiceAuth(
        val serviceToken: String,
        val userToken: String? = null
    ) : AuthContext
}

val AuthContextPlugin = createClientPlugin("AuthContext") {
    onRequest { request, _ ->
        when (val ctx = coroutineContext[AuthContext]) {
            is AuthContext.UserAuth -> {
                request.header("Authorization", "Bearer ${ctx.accessToken}")
            }
            is AuthContext.ServiceAuth -> {
                request.header("Authorization", "Bearer ${ctx.serviceToken}")
                ctx.userToken?.let {
                    request.header("X-User-Context", it)
                }
            }
            null -> { /* No auth context */ }
        }
    }
}
```

### 4.6 Token Validation

**Validation Steps**:
1. Extract `Authorization` header (service token)
2. Validate JWT signature using JWKS
3. Verify issuer, audience, expiration
4. Extract service identity and scopes
5. If `X-User-Context` present, validate user token
6. Build combined authorization context

**Result Types**:
```kotlin
sealed interface AuthResult<out T> {
    data class Authenticated<T>(val value: T) : AuthResult<T>
    data class Unauthenticated(val reason: String) : AuthResult<Nothing>
    data class Invalid(val reason: String) : AuthResult<Nothing>
}

sealed interface JwtVerifyResult {
    data class Valid(val principal: DecodedJWT) : JwtVerifyResult
    data class Invalid(val reason: String) : JwtVerifyResult
    data class Expired(val expiredAt: Instant) : JwtVerifyResult
}
```

**Implementation**:
```kotlin
suspend fun validateServiceRequest(call: ApplicationCall): AuthResult<ServiceAuthContext> {
    val serviceToken = call.request.bearer()
        ?: return AuthResult.Unauthenticated("Missing service token")

    val servicePrincipal = when (val result = jwtVerifier.verify(serviceToken)) {
        is JwtVerifyResult.Valid -> result.principal
        is JwtVerifyResult.Invalid -> return AuthResult.Invalid(result.reason)
        is JwtVerifyResult.Expired -> return AuthResult.Invalid("Token expired at ${result.expiredAt}")
    }

    val serviceId = servicePrincipal.subject
    val scopes = servicePrincipal.getClaim("scope").asString().split(" ")

    val userPrincipal = call.request.header("X-User-Context")?.let { userToken ->
        when (val result = jwtVerifier.verify(userToken)) {
            is JwtVerifyResult.Valid -> result.principal
            else -> null // User token is optional, ignore if invalid
        }
    }

    return AuthResult.Authenticated(
        ServiceAuthContext(
            serviceId = serviceId,
            servicePrincipal = servicePrincipal,
            scopes = scopes,
            userPrincipal = userPrincipal
        )
    )
}
```

### 4.7 Service Token Caching

**Token Reuse**:
- Cache service tokens until 5 minutes before expiration
- Thread-safe cache with atomic refresh
- Per-target-service token (different audiences)

**Result Types**:
```kotlin
sealed interface TokenResult {
    data class Success(val token: String, val expiresAt: Instant) : TokenResult
    data class Failed(val reason: String, val cause: Throwable? = null) : TokenResult
}
```

**Implementation**:
```kotlin
class ServiceTokenProvider(
    private val clientId: String,
    private val clientSecret: String,
    private val tokenEndpoint: String
) {
    private val tokenCache = ConcurrentHashMap<String, CachedToken>()

    suspend fun getToken(targetService: String): TokenResult {
        val cached = tokenCache[targetService]
        if (cached != null && !cached.isExpiringSoon()) {
            return TokenResult.Success(cached.token, cached.expiresAt)
        }

        return fetchAndCacheToken(targetService)
    }

    private suspend fun fetchAndCacheToken(targetService: String): TokenResult {
        return try {
            val response = httpClient.post(tokenEndpoint) {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(FormDataContent(Parameters.build {
                    append("grant_type", "client_credentials")
                    append("client_id", clientId)
                    append("client_secret", clientSecret)
                    append("audience", "service:$targetService")
                }))
            }.body<TokenResponse>()

            val expiresAt = Clock.System.now().plus(response.expiresIn.seconds)
            tokenCache[targetService] = CachedToken(
                token = response.accessToken,
                expiresAt = expiresAt
            )

            TokenResult.Success(response.accessToken, expiresAt)
        } catch (e: Exception) {
            TokenResult.Failed("Failed to acquire service token", e)
        }
    }
}
```

---

## 5. Authorization Model

### 5.1 Role-Based Access Control (RBAC)

**User Roles** (from Keycloak realm roles):
- `customer`: Regular user, can browse, order, manage profile
- `admin`: Administrative access, can view all orders, manage menu
- `support`: Customer support, can view user orders, assist with issues

**Service Roles** (from Keycloak service account roles):
- `service:basket:read`: Read basket data
- `service:basket:write`: Modify basket data
- `service:order:read`: Read order data
- `service:order:write`: Create and update orders
- `service:menu:read`: Read menu items
- `service:payment:initiate`: Initiate payment transactions

### 5.2 Scope-Based Authorization

**OAuth2 Scopes** for service-to-service calls:
```
Format: {service}:{resource}:{action}

Examples:
- basket:items:read
- basket:items:write
- order:orders:create
- order:orders:read
- payment:transactions:initiate
```

**Ergonomic Routing API**:

The routing API provides a convenient DSL that automatically handles authentication and scope validation, making `AuthContext` available in the coroutine context:

```kotlin
// User authentication - requires valid user session
fun Route.example() = secureUser {
    // AuthContext.User is available in currentCoroutineContext
    get("/profile") {
        val user = userSession()
        call.respond(user)
    }
}

// User authentication with required scopes (vararg)
fun Route.basketRoutes() = secureUser("basket:items:write") {
    post("/items") {
        val user = userSession()
        val item = call.receive<BasketItem>()
        // Scope "basket:items:write" is automatically validated
        basketRepository.addItem(user.id, item)
        call.respond(HttpStatusCode.Created)
    }
}

// Multiple scopes can be required
fun Route.adminRoutes() = secureUser("admin:users:read", "admin:users:write") {
    // All specified scopes are validated
    get("/users") { /* ... */ }
}

// Service authentication - requires valid service token
fun Route.internalAPI() = secureService {
    // AuthContext.Service is available in currentCoroutineContext
    get("/health") {
        val service = serviceSession()
        call.respond(mapOf("caller" to service.serviceId))
    }
}

// Public routes - AuthContext available only if authenticated
fun Route.publicRoutes() = public {
    get("/menu") {
        // currentCoroutineContext has AuthContext if logged in
        val authOrNull = authContextOrNull() // AuthContext.Service OR AuthContext.User OR null
        val items = if (authOrNull != null) {
            menuRepository.findAllWithPricing()
        } else {
            menuRepository.findPublicItems()
        }
        call.respond(items)
    }
}
```

**Internal Implementation Details**:

```kotlin
// These are the internal mechanisms - users work with the ergonomic API above

sealed interface AuthorizationResult {
    data object Authorized : AuthorizationResult
    data class Unauthorized(val missingScope: String) : AuthorizationResult
    data class Forbidden(val reason: String) : AuthorizationResult
}

// Scope validation is handled internally by secureUser/secureService
suspend fun requireScope(scopes: List<String>, authContext: AuthContext): AuthorizationResult {
    val availableScopes = when (authContext) {
        is AuthContext.User -> authContext.scopes
        is AuthContext.Service -> authContext.scopes
    }

    val missingScopes = scopes.filterNot { it in availableScopes }
    return if (missingScopes.isEmpty()) {
        AuthorizationResult.Authorized
    } else {
        AuthorizationResult.Unauthorized(missingScopes.first())
    }
}
```

### 5.3 Attribute-Based Access Control (ABAC)

**Resource Ownership**:
- Users can only access their own resources
- Admins can access any resource
- Services can access resources within their scope

**Implementation with Result Types**:
```kotlin
context(ServiceAuthContext)
suspend fun requireResourceOwnership(
    resourceOwnerId: String,
    requiredRole: String? = null
): AuthorizationResult {
    val userId = userPrincipal?.subject
        ?: return AuthorizationResult.Forbidden("User context required")

    val roles = userPrincipal.getClaim("realm_access")
        .asMap()["roles"] as? List<String> ?: emptyList()

    val isAdmin = roles.contains("admin")
    val hasRequiredRole = requiredRole == null || roles.contains(requiredRole)
    val isOwner = userId == resourceOwnerId

    return when {
        !hasRequiredRole -> AuthorizationResult.Forbidden("Missing required role: $requiredRole")
        isAdmin -> AuthorizationResult.Authorized
        isOwner -> AuthorizationResult.Authorized
        else -> AuthorizationResult.Forbidden("Not resource owner")
    }
}

// Usage example
context(ServiceAuthContext)
suspend fun Route.orderRoutes() {
    get("/orders/{id}") {
        val orderId = call.parameters["id"]?.toLongOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid order ID")

        val order = orderRepository.findById(orderId)
            ?: return@get call.respond(HttpStatusCode.NotFound, "Order not found")

        when (requireResourceOwnership(order.buyerId)) {
            is AuthorizationResult.Authorized -> {
                call.respond(order)
            }
            is AuthorizationResult.Forbidden -> {
                call.respond(HttpStatusCode.Forbidden, "Access denied")
            }
            is AuthorizationResult.Unauthorized -> {
                call.respond(HttpStatusCode.Unauthorized)
            }
        }
    }
}
```

### 5.4 Authorization Plugin

**Ergonomic Routing DSL**:

The routing API is designed to be simple and convenient, eliminating boilerplate while maintaining type safety:

```kotlin
// secureUser - Validates user authentication and optional scopes
fun Route.secureUser(vararg scopes: String, build: Route.() -> Unit): Route {
    return intercept(ApplicationCallPipeline.Plugins) {
        when (val result = validateUserRequest(call)) {
            is AuthResult.Authenticated -> {
                val authContext = AuthContext.User(
                    principal = result.value,
                    accessToken = result.accessToken,
                    scopes = result.scopes
                )

                // Validate required scopes if any
                if (scopes.isNotEmpty()) {
                    when (requireScope(scopes.toList(), authContext)) {
                        is AuthorizationResult.Authorized -> {
                            withContext(authContext) { proceed() }
                        }
                        is AuthorizationResult.Unauthorized -> {
                            call.respond(HttpStatusCode.Forbidden, "Missing required scope")
                            finish()
                        }
                        is AuthorizationResult.Forbidden -> {
                            call.respond(HttpStatusCode.Forbidden, "Access denied")
                            finish()
                        }
                    }
                } else {
                    withContext(authContext) { proceed() }
                }
            }
            is AuthResult.Unauthenticated -> {
                call.respond(HttpStatusCode.Unauthorized, result.reason)
                finish()
            }
            is AuthResult.Invalid -> {
                call.respond(HttpStatusCode.Unauthorized, result.reason)
                finish()
            }
        }
    }.apply(build)
}

// secureService - Validates service authentication
fun Route.secureService(vararg scopes: String, build: Route.() -> Unit): Route {
    return intercept(ApplicationCallPipeline.Plugins) {
        when (val result = validateServiceRequest(call)) {
            is AuthResult.Authenticated -> {
                val authContext = AuthContext.Service(
                    serviceId = result.value.serviceId,
                    principal = result.value.servicePrincipal,
                    scopes = result.value.scopes,
                    userContext = result.value.userPrincipal?.let { /* ... */ }
                )

                // Validate required scopes if any
                if (scopes.isNotEmpty()) {
                    when (requireScope(scopes.toList(), authContext)) {
                        is AuthorizationResult.Authorized -> {
                            withContext(authContext) { proceed() }
                        }
                        is AuthorizationResult.Unauthorized -> {
                            call.respond(HttpStatusCode.Forbidden, "Missing required scope")
                            finish()
                        }
                        is AuthorizationResult.Forbidden -> {
                            call.respond(HttpStatusCode.Forbidden, "Access denied")
                            finish()
                        }
                    }
                } else {
                    withContext(authContext) { proceed() }
                }
            }
            is AuthResult.Unauthenticated -> {
                call.respond(HttpStatusCode.Unauthorized, result.reason)
                finish()
            }
            is AuthResult.Invalid -> {
                call.respond(HttpStatusCode.Unauthorized, result.reason)
                finish()
            }
        }
    }.apply(build)
}

// public - Optional authentication, AuthContext available if logged in
fun Route.public(build: Route.() -> Unit): Route {
    return intercept(ApplicationCallPipeline.Plugins) {
        // Try to extract AuthContext from any available source
        val authContext = validateServiceRequest(call).let { result ->
            if (result is AuthResult.Authenticated) {
                AuthContext.Service(/* ... */)
            } else null
        } ?: validateUserRequest(call).let { result ->
            if (result is AuthResult.Authenticated) {
                AuthContext.User(/* ... */)
            } else null
        }

        if (authContext != null) {
            withContext(authContext) { proceed() }
        } else {
            proceed()
        }
    }.apply(build)
}

// Convenience accessors available in route handlers
suspend fun ApplicationCall.userSession(): UserSession {
    val authContext = coroutineContext[AuthContext] as? AuthContext.User
        ?: throw IllegalStateException("User authentication required")
    return authContext.toUserSession()
}

suspend fun ApplicationCall.serviceSession(): ServiceSession {
    val authContext = coroutineContext[AuthContext] as? AuthContext.Service
        ?: throw IllegalStateException("Service authentication required")
    return authContext.toServiceSession()
}

suspend fun ApplicationCall.authContextOrNull(): AuthContext? {
    return coroutineContext[AuthContext]
}
```

**Enhanced AuthContext**:

```kotlin
sealed interface AuthContext : AbstractCoroutineContextElement {
    companion object Key : CoroutineContext.Key<AuthContext>
    override val key: CoroutineContext.Key<*> get() = AuthContext

    data class User(
        val principal: DecodedJWT,
        val accessToken: String,
        val scopes: List<String>
    ) : AuthContext {
        val userId: String get() = principal.subject
        val roles: List<String> get() = principal.getClaim("realm_access")
            .asMap()["roles"] as? List<String> ?: emptyList()
        val isAdmin: Boolean get() = roles.contains("admin")
    }

    data class Service(
        val serviceId: String,
        val principal: DecodedJWT,
        val scopes: List<String>,
        val userContext: UserContext? = null
    ) : AuthContext {
        data class UserContext(
            val principal: DecodedJWT,
            val userId: String,
            val roles: List<String>
        )
    }
}
```

### 5.5 Complete Usage Examples

**Design Philosophy**:
- Minimize boilerplate while maintaining type safety
- Make authentication context available via coroutine context
- Scope validation handled automatically when scopes are specified
- Convenient accessor functions for common use cases
- Pattern follows existing `UserSessionScope` in the codebase

**User Authentication Examples**:
```kotlin
// Simple user authentication - no scope required
fun Route.profileRoutes() = secureUser {
    get("/profile") {
        val user = userSession()
        call.respond(profileRepository.findByUserId(user.userId))
    }

    put("/profile") {
        val user = userSession()
        val updates = call.receive<ProfileUpdate>()
        profileRepository.update(user.userId, updates)
        call.respond(HttpStatusCode.OK)
    }
}

// User authentication with single scope
fun Route.basketRoutes() = secureUser("basket:items:write") {
    post("/items") {
        val user = userSession()
        val item = call.receive<BasketItem>()
        basketRepository.addItem(user.userId, item)
        call.respond(HttpStatusCode.Created)
    }
}

// User authentication with multiple scopes
fun Route.adminRoutes() = secureUser("admin:users:read", "admin:users:write") {
    get("/users") {
        val user = userSession()
        if (!user.isAdmin) {
            call.respond(HttpStatusCode.Forbidden)
            return@get
        }
        call.respond(userRepository.findAll())
    }
}
```

**Service Authentication Examples**:
```kotlin
// Service-to-service internal API
fun Route.internalOrderAPI() = secureService {
    get("/orders/{id}/status") {
        val service = serviceSession()
        val orderId = call.parameters["id"]?.toLongOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest)

        val order = orderRepository.findById(orderId)
            ?: return@get call.respond(HttpStatusCode.NotFound)

        call.respond(order.status)
    }
}

// Service authentication with scope validation
fun Route.paymentCallbacks() = secureService("payment:callbacks:process") {
    post("/webhook") {
        val service = serviceSession()
        val event = call.receive<PaymentEvent>()
        paymentProcessor.handleWebhook(event)
        call.respond(HttpStatusCode.OK)
    }
}
```

**Public Routes with Optional Authentication**:
```kotlin
fun Route.menuRoutes() = public {
    get("/items") {
        val authContext = authContextOrNull()

        // Return different data based on authentication
        val items = when (authContext) {
            is AuthContext.User -> {
                // Authenticated user - show personalized menu with pricing
                menuRepository.findAllWithPricing(authContext.userId)
            }
            is AuthContext.Service -> {
                // Service call - show full menu with wholesale pricing
                menuRepository.findAllWithWholesalePricing()
            }
            null -> {
                // Anonymous - show public menu items only
                menuRepository.findPublicItems()
            }
        }

        call.respond(items)
    }
}
```

**Complete Application Setup**:
```kotlin
fun Application.basketService() {
    routing {
        // Public endpoints
        public {
            get("/health") {
                call.respond(mapOf("status" to "healthy"))
            }
        }

        // User-facing API
        route("/api") {
            secureUser {
                basketRoutes()
                checkoutRoutes()
            }
        }

        // Internal service API
        route("/internal") {
            secureService {
                basketSyncRoutes()
                basketEventsRoutes()
            }
        }
    }
}

fun Route.basketRoutes() {
    route("/basket") {
        get {
            val user = userSession()
            val basket = basketRepository.findByBuyerId(user.userId)
            call.respond(basket)
        }

        post("/items") {
            val user = userSession()
            val item = call.receive<BasketItem>()
            basketRepository.addItem(user.userId, item)
            call.respond(HttpStatusCode.Created)
        }
    }
}
```

**Benefits**:
1. **Minimal boilerplate** - Authentication and scope validation handled automatically
2. **Type-safe** - `AuthContext` available in coroutine context with correct type
3. **Convenient** - `userSession()`, `serviceSession()`, `authContextOrNull()` accessors
4. **Clear intent** - `secureUser`, `secureService`, `public` make security boundaries explicit
5. **Composable** - Easy to mix authentication types in different route branches
6. **Consistent** - Follows existing patterns in the codebase (e.g., `UserSessionScope`)

---

## 6. Security Mechanisms

### 6.1 Transport Security

**TLS/mTLS**:
- All external traffic over HTTPS (TLS 1.3)
- Internal service mesh with mTLS (optional, via Istio/Linkerd)
- Certificate management via cert-manager in Kubernetes

### 6.2 Secrets Management

**Kubernetes Secrets**:
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: webapp-service-credentials
  namespace: foodies
type: Opaque
data:
  client-id: c2VydmljZS13ZWJhcHA=
  client-secret: <base64-encoded-secret>
```

**Environment Variable Injection**:
```yaml
env:
  - name: SERVICE_CLIENT_ID
    valueFrom:
      secretKeyRef:
        name: webapp-service-credentials
        key: client-id
  - name: SERVICE_CLIENT_SECRET
    valueFrom:
      secretKeyRef:
        name: webapp-service-credentials
        key: client-secret
```

### 6.3 Token Validation

**JWT Validation Requirements**:
1. Signature verification using JWKS
2. Issuer claim matches expected issuer
3. Audience claim matches service identity
4. Expiration time (exp) not exceeded
5. Not-before time (nbf) if present
6. Issued-at time (iat) within acceptable range

**JWKS Caching**:
- Cache JWKS response for 1 hour
- Refresh on signature verification failure
- Validate key ID (kid) matches

### 6.4 Rate Limiting

**Per-Client Rate Limits**:
```kotlin
install(RateLimit) {
    register(RateLimitName("service-to-service")) {
        rateLimiter(limit = 1000, refillPeriod = 60.seconds)
        requestKey { call ->
            val authContext = call.attributes.getOrNull(ServiceAuthContextKey)
            authContext?.serviceId ?: "anonymous"
        }
    }
}
```

### 6.5 Audit Logging

**Structured Logging** (OpenTelemetry):
```kotlin
fun logAuthEvent(
    event: String,
    serviceId: String?,
    userId: String?,
    resource: String,
    outcome: String,
    reason: String? = null
) {
    logger.info(
        "auth_event" to event,
        "service_id" to serviceId,
        "user_id" to userId,
        "resource" to resource,
        "outcome" to outcome,
        "reason" to reason,
        "timestamp" to Clock.System.now().toString()
    )
}
```

**Audit Events**:
- Authentication success/failure
- Authorization decision (allow/deny)
- Token issuance/refresh
- Session creation/termination
- Service-to-service calls

---

## 7. Implementation Requirements

### 7.1 Service Configuration Schema

**Enhanced application.yaml**:
```yaml
auth:
  issuer: "$AUTH_ISSUER:http://localhost:8000/realms/foodies-keycloak"
  audience: "$AUTH_AUDIENCE:foodies"

service:
  identity:
    clientId: "$SERVICE_CLIENT_ID:service-webapp"
    clientSecret: "$SERVICE_CLIENT_SECRET"
    tokenEndpoint: "$TOKEN_ENDPOINT:http://keycloak:8000/realms/foodies-keycloak/protocol/openid-connect/token"

  authorization:
    enforceServiceAuth: "$ENFORCE_SERVICE_AUTH:true"
    enforceUserContext: "$ENFORCE_USER_CONTEXT:false"

security:
  session:
    ttl: "$SESSION_TTL:3600"
    redis:
      host: "$REDIS_HOST:localhost"
      port: "$REDIS_PORT:6379"

  tls:
    enabled: "$TLS_ENABLED:true"
    certPath: "$TLS_CERT_PATH:/etc/certs/tls.crt"
    keyPath: "$TLS_KEY_PATH:/etc/certs/tls.key"
```

### 7.2 Shared Library Components

**server-shared module additions**:
```
server-shared/
├── openid/
│   ├── AuthContext.kt (existing, enhance with sealed interface)
│   ├── OpenIdConfiguration.kt (existing)
│   ├── Security.kt (existing)
│   ├── ServiceTokenProvider.kt (new - token acquisition)
│   ├── ServiceAuthContext.kt (new - auth context data)
│   ├── ServiceAuthScope.kt (new - composable DSL scope)
│   ├── AuthResult.kt (new - result types)
│   ├── AuthorizationResult.kt (new - authorization result types)
│   ├── TokenResult.kt (new - token acquisition result types)
│   ├── JwtVerifyResult.kt (new - JWT validation result types)
│   └── Authorization.kt (new - authorization helpers with context receivers)
```

### 7.3 Migration Checklist

**Phase 1: Service Identity**
- [ ] Register services as Keycloak clients
- [ ] Generate client credentials and store in Kubernetes secrets
- [ ] Implement ServiceTokenProvider
- [ ] Add service token acquisition to service startup

**Phase 2: Service Authentication**
- [ ] Implement dual-token AuthContext
- [ ] Update HTTP clients to include service tokens
- [ ] Add service token validation to all services
- [ ] Deploy with backward compatibility (accept both patterns)

**Phase 3: Authorization**
- [ ] Define service scopes in Keycloak
- [ ] Implement scope validation plugin
- [ ] Add authorization checks to endpoints
- [ ] Implement ABAC for resource ownership

**Phase 4: Enforcement**
- [ ] Enable strict service authentication enforcement
- [ ] Remove backward compatibility
- [ ] Monitor authentication failures
- [ ] Update documentation

### 7.4 Testing Requirements

**Unit Tests**:
- Token validation logic
- Scope parsing and validation
- Authorization decision logic
- Token caching and refresh

**Integration Tests**:
- End-to-end user authentication flow
- Service-to-service authentication
- Token refresh scenarios
- Authorization enforcement
- Failure modes (expired tokens, invalid signatures)

**Security Tests**:
- Token tampering detection
- Replay attack prevention
- Scope escalation attempts
- Missing authentication handling

---

## 8. Migration Strategy

### 8.1 Backward Compatibility

**Dual-Mode Operation**:
```kotlin
val authContext = if (call.request.header("Authorization")?.startsWith("Bearer ") == true) {
    // New pattern: service token in Authorization header
    validateServiceRequest(call)
} else {
    // Legacy pattern: user token only
    validateLegacyRequest(call)
}
```

**Feature Flag**:
```yaml
service:
  authorization:
    enforceServiceAuth: false  # Allow legacy pattern during migration
```

### 8.2 Rollout Plan

**Week 1: Infrastructure**
- Register services in Keycloak
- Create Kubernetes secrets
- Deploy updated server-shared library

**Week 2: Service Updates**
- Update WebApp to use dual-token pattern
- Update Menu service
- Update Basket service

**Week 3: Service Updates**
- Update Order service
- Update Profile service
- Update Payment service

**Week 4: Validation**
- Monitor authentication metrics
- Verify no legacy pattern usage
- Enable enforcement mode
- Remove backward compatibility code

### 8.3 Rollback Plan

**Rollback Triggers**:
- Authentication failure rate > 1%
- Service-to-service call failure rate > 5%
- Unable to obtain service tokens

**Rollback Steps**:
1. Disable enforcement: Set `enforceServiceAuth: false`
2. Revert to previous deployment
3. Investigate root cause
4. Fix and re-test before retry

---

## 9. Operational Considerations

### 9.1 Monitoring

**Metrics** (Prometheus/OpenTelemetry):
- `auth_requests_total{service, outcome}`: Authentication attempts
- `auth_duration_seconds{service}`: Authentication latency
- `token_refresh_total{service}`: Token refresh count
- `authorization_decisions_total{service, resource, outcome}`: Authorization decisions
- `service_token_cache_hits_total`: Token cache effectiveness

**Alerts**:
- Authentication failure rate > 1% for 5 minutes
- Service token acquisition failure
- JWKS fetch failures
- Unusual authorization denial patterns

### 9.2 Logging

**Structured Log Fields**:
- `service_id`: Calling service
- `user_id`: User context (if present)
- `resource`: Resource being accessed
- `action`: Operation attempted
- `outcome`: success/failure
- `latency_ms`: Operation duration
- `trace_id`: Distributed tracing ID

**Log Levels**:
- INFO: Successful authentication/authorization
- WARN: Authentication failures, missing tokens
- ERROR: System errors (JWKS unavailable, token service down)

### 9.3 Performance Optimization

**Token Caching**:
- Service tokens cached until expiration
- JWKS cached for 1 hour
- Redis session caching

**Connection Pooling**:
- HTTP client connection pooling to Keycloak
- Redis connection pooling

**Async Processing**:
- Non-blocking JWT validation
- Coroutine-based token refresh

---

## 10. Appendices

### Appendix A: Security Threat Model

**Threats**:
1. Token Theft: Mitigated by HTTPS, HTTP-only cookies, short expiration
2. Token Replay: Mitigated by short expiration, nonce (for critical operations)
3. Service Impersonation: Mitigated by service authentication with secrets
4. Privilege Escalation: Mitigated by scope validation, RBAC
5. Session Hijacking: Mitigated by secure cookies, Redis session storage

### Appendix B: Compliance Considerations

**GDPR**:
- Session data contains minimal PII
- Audit logs anonymized where possible
- Token expiration ensures data minimization

**PCI DSS** (if applicable to payment service):
- No card data in tokens or sessions
- TLS for all payment-related communication
- Service isolation for payment processing

### Appendix C: Performance Benchmarks

**Target Latencies**:
- Token validation: < 10ms
- Token acquisition (cached): < 1ms
- Token acquisition (fresh): < 100ms
- Session lookup: < 5ms (Redis)
- Authorization check: < 5ms

### Appendix D: Configuration Examples

**Keycloak Service Client**:
```json
{
  "clientId": "service-webapp",
  "name": "WebApp Service Account",
  "description": "Service account for webapp to call downstream services",
  "enabled": true,
  "publicClient": false,
  "protocol": "openid-connect",
  "standardFlowEnabled": false,
  "directAccessGrantsEnabled": false,
  "serviceAccountsEnabled": true,
  "authorizationServicesEnabled": true,
  "attributes": {
    "access.token.lifespan": "900"
  },
  "defaultClientScopes": [
    "service:basket",
    "service:menu",
    "service:order"
  ]
}
```

### Appendix E: References

1. OAuth 2.0 RFC 6749: https://tools.ietf.org/html/rfc6749
2. OAuth 2.0 Bearer Token Usage RFC 6750: https://tools.ietf.org/html/rfc6750
3. JWT RFC 7519: https://tools.ietf.org/html/rfc7519
4. OAuth 2.0 PKCE RFC 7636: https://tools.ietf.org/html/rfc7636
5. OWASP Top 10: https://owasp.org/www-project-top-ten/
6. NIST Zero Trust Architecture: https://www.nist.gov/publications/zero-trust-architecture

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-01-20 | Claude Code | Initial specification |


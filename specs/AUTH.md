# Authentication & Authorization Specification

## Overview

This document specifies the authentication and authorization model for the Foodies application. The system uses OAuth 2.0 / OpenID Connect with Keycloak as the identity provider, implementing a scope-based authorization model with audience validation for service isolation.

## Scope Model

### Primary Roles

The Foodies application uses two primary roles to distinguish between user types:

- **`user`** - Regular users who can access their own resources
- **`admin`** - Administrators who can perform management operations

### Service Scopes

Each microservice has its own scope that grants access to that service:

- **`menu`** - Menu service access
- **`basket`** - Basket service access
- **`payment`** - Payment service access
- **`profile`** - User profile service access

### Scope Assignment Rules

1. **Regular Users** receive:
    - `user` role
    - Service scopes for user-facing operations: `basket`, `payment`, `profile`
    - Operation scopes: `basket:read`, `basket:write`, `payment:read`, `payment:write`, `profile:read`, `profile:write`
    - Menu access through service scopes: `menu:read`

2. **Administrators** receive:
    - `admin` role
    - All service scopes: `menu`, `basket`, `payment`, `profile`
    - All operation scopes including write operations: `menu:write`, etc.

3. **Service Clients** (for service-to-service communication) receive:
    - Specific service scopes they need to call
    - Example: Payment service client gets `basket` scope to retrieve basket contents

## Audience Model

### Audience Configuration

Each service validates that tokens are issued for its specific audience. This prevents token misuse across services.

| Service | Audience Value |
|---------|----------------|
| Menu    | `menu`         |
| Basket  | `basket`       |
| Payment | `payment`      |
| Profile | `profile`      |
| Webapp  | `webapp`       |

### Audience Validation

- Each service **MUST** validate the `aud` claim matches its expected audience
- Services **MAY** accept multiple audiences if they need to handle tokens from different clients
- The webapp uses `webapp` audience for session management

## Authentication Patterns

### 1. User Authentication (OAuth 2.0 Authorization Code Flow)

**Used by:** Webapp

**Flow:**
```
User → Webapp → Keycloak → User Login → Keycloak Callback → Webapp → Session Created
```

**Configuration:**
```yaml
security:
  issuer: "http://foodies.local:8000/realms/foodies-keycloak"
  clientId: "foodies-webapp"
  clientSecret: "${WEBAPP_CLIENT_SECRET}"
  redirectUri: "http://foodies.local:8080/oauth/callback"
```

**Requested Scopes:**
- `openid`, `profile`, `email` (OIDC standard)
- `user` (primary role)
- `basket`, `payment`, `profile` (service scopes)
- `basket:read`, `basket:write`, etc. (operation scopes)

### 2. Service-to-Service Authentication (Client Credentials Flow)

**Used by:** Backend services calling other backend services

**Flow:**
```
Service A → Keycloak (Client Credentials) → Access Token → Service B
```

**Configuration Example (Payment Service calling Basket Service):**
```yaml
clients:
  basket:
  
    clientId: "payment-service"
    clientSecret: "${PAYMENT_CLIENT_SECRET}"
    scope: "basket basket:read"
    audience: "basket"
```

**Use Cases:**
- Payment service retrieving basket contents before processing payment
- Profile service updating user information based on events
- Background jobs that need to interact with services

### 3. Token Forwarding

**Used by:** Services processing user-initiated requests

**Pattern:**
When a user makes a request to the webapp that requires calling backend services, the webapp forwards the user's access token:

```
User → Webapp (with session) → Extract access_token → Service (with Bearer token)
```

**Implementation:**
- Webapp stores `access_token` in session
- Extract token from session when calling backend services
- Attach as `Authorization: Bearer <token>` header
- Backend services validate token and extract user identity from `sub` claim

## Authorization Patterns

### 1. Resource Ownership (User Role)

Users with `user` role can only access **their own resources**. This is enforced by:

1. Extracting user ID from JWT `sub` claim
2. Comparing with resource owner ID
3. Rejecting requests that don't match

**Example (Basket Service):**
```kotlin
route("/basket") {
    secured {
        get {
            val userId = call.principal<JWTPrincipal>()?.subject
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            // userId is used to filter basket items
            val basket = basketService.getBasket(userId)
            call.respond(basket)
        }
    }
}
```

### 2. Role-Based Authorization (Admin Operations)

Operations that require `admin` role:

- Creating, updating, deleting menu items (`menu:write`)
- Viewing all user payments (`payment` + `admin`)
- Administrative user management

**Example (Menu Service):**
```kotlin
route("/menu") {
    // Read operations - requires user or admin
    secured {
        get {
            val items = menuService.getAllItems()
            call.respond(items)
        }
    }

    // Write operations - requires admin role
    secured("ADMIN") {
        post {
            val item = call.receive<MenuItem>()
            menuService.createItem(item)
            call.respond(HttpStatusCode.Created)
        }
    }
}
```

### 3. Audience Validation

Each service validates the `aud` claim to ensure tokens are intended for it:

**Example (Shared Authentication Extension):**
```kotlin
fun Application.configureServiceAuthentication(audience: String) {
    val config = environment.config.config("auth")
    val issuer = config.property("issuer").getString()

    authentication {
        jwt {
            realm = "foodies"
            verifier(jwkProvider, issuer) {
                acceptLeeway(3)
            }
            validate { credential ->
                // Validate audience
                if (audience !in credential.payload.audience) {
                    log.warn("Token audience mismatch. Expected: $audience, Got: ${credential.payload.audience}")
                    return@validate null
                }

                // Validate required claims
                if (credential.payload.subject.isNullOrEmpty()) {
                    log.warn("Token missing subject claim")
                    return@validate null
                }

                JWTPrincipal(credential.payload)
            }
        }
    }
}
```

## Implementation Guide

### Shared Authentication Module

Create a shared authentication module in `server-shared` to avoid code duplication:

**Location:** `/server-shared/src/main/kotlin/io/ktor/foodies/server/auth/`

**Files:**
```
auth/
├── JwtAuthentication.kt      # JWT configuration and validation
├── ScopeValidation.kt         # Scope and role checking utilities
├── SecuredPlugin.kt           # Secured routing plugin combining auth + authz
├── UserIdentity.kt            # User identity extraction
└── ClientCredentials.kt       # Client credentials flow for service-to-service
```

### Service-Specific Configuration

Each service configures authentication with its specific audience:

**Basket Service:**
```kotlin
fun Application.configureSecurity() {
    configureServiceAuthentication(audience = "basket")
}
```

**Menu Service:**
```kotlin
fun Application.configureSecurity() {
    configureServiceAuthentication(audience = "menu")
}
```

**Payment Service:**
```kotlin
fun Application.configureSecurity() {
    configureServiceAuthentication(audience = "payment")
}
```

### Scope Validation Utilities

**ScopeValidation.kt:**
```kotlin
fun JWTPrincipal.hasScope(scope: String): Boolean {
    val scopes = payload.getClaim("scope").asString()?.split(" ") ?: emptyList()
    return scope in scopes
}

fun JWTPrincipal.hasRole(role: String): Boolean {
    val scopes = payload.getClaim("scope").asString()?.split(" ") ?: emptyList()
    return role.lowercase() in scopes
}

fun JWTPrincipal.requireScope(scope: String): Boolean {
    if (!hasScope(scope)) {
        throw InsufficientScopeException("Required scope: $scope")
    }
    return true
}

fun JWTPrincipal.requireRole(role: String): Boolean {
    if (!hasRole(role)) {
        throw ForbiddenException("Required role: $role")
    }
    return true
}
```

### Secured Plugin

**SecuredPlugin.kt:**

The `secured()` plugin combines authentication and authorization in a single routing DSL function:

```kotlin
/**
 * Applies authentication and optional role-based authorization to a route.
 *
 * @param role Optional role required to access the route (e.g., "ADMIN")
 */
fun Route.secured(role: String? = null, build: Route.() -> Unit): Route {
    return authenticate {
        intercept(ApplicationCallPipeline.Call) {
            val principal = call.principal<JWTPrincipal>()
                ?: throw UnauthorizedException("Authentication required")

            // If a role is specified, validate it
            role?.let {
                if (!principal.hasRole(it)) {
                    throw ForbiddenException("Required role: $it")
                }
            }
        }
        build()
    }
}
```

**Usage Examples:**

```kotlin
// Authentication only (any authenticated user)
secured {
    get("/basket") {
        // Access for any authenticated user
    }
}

// Authentication + Role requirement
secured("ADMIN") {
    post("/menu") {
        // Only users with ADMIN role can access
    }
}
```

### User Identity Extraction

**UserIdentity.kt:**
```kotlin
data class UserIdentity(
    val userId: String,
    val username: String?,
    val email: String?,
    val scopes: Set<String>
)

fun JWTPrincipal.getUserIdentity(): UserIdentity {
    val scopes = payload.getClaim("scope").asString()
        ?.split(" ")
        ?.toSet()
        ?: emptySet()

    return UserIdentity(
        userId = payload.subject,
        username = payload.getClaim("preferred_username").asString(),
        email = payload.getClaim("email").asString(),
        scopes = scopes
    )
}

val ApplicationCall.userId: String
    get() = principal<JWTPrincipal>()?.subject
        ?: throw UnauthorizedException("Authentication required")
```

### Client Credentials Flow

**ClientCredentials.kt:**
```kotlin
class ServiceAuthClient(
    private val tokenEndpoint: String,
    private val clientId: String,
    private val clientSecret: String,
    private val httpClient: HttpClient
) {
    private var cachedToken: AccessToken? = null

    suspend fun getAccessToken(scope: String, audience: String): String {
        // Check cache
        cachedToken?.let { token ->
            if (!token.isExpired()) {
                return token.value
            }
        }

        // Request new token
        val response: TokenResponse = httpClient.submitForm(
            url = tokenEndpoint,
            formParameters = parameters {
                append("grant_type", "client_credentials")
                append("client_id", clientId)
                append("client_secret", clientSecret)
                append("scope", scope)
                append("audience", audience)
            }
        ).body()

        cachedToken = AccessToken(
            value = response.accessToken,
            expiresAt = Clock.System.now() + response.expiresIn.seconds
        )

        return response.accessToken
    }
}

private data class AccessToken(
    val value: String,
    val expiresAt: Instant
) {
    fun isExpired(): Boolean = Clock.System.now() >= expiresAt
}

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("token_type") val tokenType: String
)
```

## Keycloak Configuration

### Realm Roles

**Defined in:** `k8s/configmaps/keycloak-realm.yaml`

```yaml
roles:
  realm:
    - name: "user"
      description: "Regular user with access to their own resources"
    - name: "admin"
      description: "Administrator with management capabilities"
```

### Scopes Configuration

```yaml
clientScopes:
  - name: "menu"
    protocol: "openid-connect"
    attributes:
      "include.in.token.scope": "true"
      "display.on.consent.screen": "true"

  - name: "menu:read"
    protocol: "openid-connect"
    attributes:
      "include.in.token.scope": "true"

  - name: "menu:write"
    protocol: "openid-connect"
    attributes:
      "include.in.token.scope": "true"

  - name: "basket"
    protocol: "openid-connect"
    attributes:
      "include.in.token.scope": "true"

  - name: "basket:read"
    protocol: "openid-connect"
    attributes:
      "include.in.token.scope": "true"

  - name: "basket:write"
    protocol: "openid-connect"
    attributes:
      "include.in.token.scope": "true"

  - name: "payment"
    protocol: "openid-connect"
    attributes:
      "include.in.token.scope": "true"

  - name: "payment:read"
    protocol: "openid-connect"
    attributes:
      "include.in.token.scope": "true"

  - name: "payment:write"
    protocol: "openid-connect"
    attributes:
      "include.in.token.scope": "true"

  - name: "profile"
    protocol: "openid-connect"
    attributes:
      "include.in.token.scope": "true"

  - name: "profile:read"
    protocol: "openid-connect"
    attributes:
      "include.in.token.scope": "true"

  - name: "profile:write"
    protocol: "openid-connect"
    attributes:
      "include.in.token.scope": "true"
```

### Client Configuration

**Webapp Client:**
```yaml
clients:
  - clientId: "foodies-webapp"
    name: "Foodies Web Application"
    enabled: true
    clientAuthenticatorType: "client-secret"
    secret: "foodies_client_secret"
    redirectUris:
      - "http://foodies.local:8080/oauth/callback"
    webOrigins:
      - "http://foodies.local:8080"
    standardFlowEnabled: true
    directAccessGrantsEnabled: false
    publicClient: false
    protocol: "openid-connect"
    defaultClientScopes:
      - "openid"
      - "profile"
      - "email"
      - "user"
      - "basket"
      - "basket:read"
      - "basket:write"
      - "payment"
      - "payment:read"
      - "payment:write"
      - "profile"
      - "profile:read"
      - "profile:write"
      - "menu:read"
    attributes:
      "access.token.lifespan": "3600"
```

**Payment Service Client (for calling Basket):**
```yaml
clients:
  - clientId: "payment-service"
    name: "Payment Service"
    enabled: true
    clientAuthenticatorType: "client-secret"
    secret: "${PAYMENT_SERVICE_SECRET}"
    serviceAccountsEnabled: true
    standardFlowEnabled: false
    directAccessGrantsEnabled: false
    publicClient: false
    protocol: "openid-connect"
    defaultClientScopes:
      - "basket"
      - "basket:read"
```

### Audience Mapper Configuration

Add audience mappers to include the correct audience in tokens:

```yaml
protocolMappers:
  - name: "basket-audience"
    protocol: "openid-connect"
    protocolMapper: "oidc-audience-mapper"
    config:
      "included.client.audience": "basket"
      "id.token.claim": "false"
      "access.token.claim": "true"

  - name: "menu-audience"
    protocol: "openid-connect"
    protocolMapper: "oidc-audience-mapper"
    config:
      "included.client.audience": "menu"
      "id.token.claim": "false"
      "access.token.claim": "true"

  - name: "payment-audience"
    protocol: "openid-connect"
    protocolMapper: "oidc-audience-mapper"
    config:
      "included.client.audience": "payment"
      "id.token.claim": "false"
      "access.token.claim": "true"

  - name: "profile-audience"
    protocol: "openid-connect"
    protocolMapper: "oidc-audience-mapper"
    config:
      "included.client.audience": "profile"
      "id.token.claim": "false"
      "access.token.claim": "true"
```

## Migration Path

### Current State

1. **Webapp:** Uses OAuth with session-based authentication ✓
2. **Basket Service:** JWT authentication configured but no scope validation
3. **Menu Service:** No authentication
4. **Payment Service:** No authentication
5. **Profile Service:** No HTTP endpoints requiring auth

### Migration Steps

#### Phase 1: Shared Authentication Module
- [ ] Create `server-shared/src/main/kotlin/io/ktor/foodies/server/auth/` module
- [ ] Implement `JwtAuthentication.kt` with audience validation
- [ ] Implement `ScopeValidation.kt` utilities with role checking
- [ ] Implement `SecuredPlugin.kt` for combined auth + authz
- [ ] Implement `UserIdentity.kt` extraction
- [ ] Implement `ClientCredentials.kt` for service-to-service auth

#### Phase 2: Update Keycloak Configuration
- [ ] Add `admin` realm role to `keycloak-realm.yaml`
- [ ] Add service scopes (`menu`, `basket`, `payment`, `profile`)
- [ ] Add operation scopes (`menu:read`, `menu:write`, etc.)
- [ ] Add audience mappers for each service
- [ ] Create service clients for service-to-service auth
- [ ] Update webapp client with new scopes

#### Phase 3: Update Services
- [ ] **Basket Service:**
    - Use shared authentication module
    - Add audience validation (`basket`)
    - Replace `authenticate` blocks with `secured()` plugin
    - Keep user-based resource filtering

- [ ] **Menu Service:**
    - Add JWT authentication
    - Add audience validation (`menu`)
    - Protect all routes with `secured()` plugin
    - Use `secured("ADMIN")` for write operations (POST/PUT/DELETE)

- [ ] **Payment Service:**
    - Add JWT authentication
    - Add audience validation (`payment`)
    - Protect all routes with `secured()` plugin
    - Add client credentials client for calling basket service
    - Use `secured("ADMIN")` for admin payment operations

- [ ] **Profile Service:**
    - Add JWT authentication when HTTP endpoints are added
    - Add audience validation (`profile`)

#### Phase 4: Update Webapp
- [ ] Request new scopes in OAuth flow
- [ ] Store `access_token` in session (not `id_token`)
- [ ] Implement token forwarding to backend services using `access_token`
- [ ] Handle 401/403 responses gracefully
- [ ] Update session management if needed

#### Phase 5: Testing
- [ ] Test user access to own resources
- [ ] Test user cannot access other users' resources
- [ ] Test admin operations require admin role with `secured("ADMIN")`
- [ ] Test audience validation rejects wrong audience
- [ ] Test service-to-service authentication
- [ ] Test scope validation for all operations
- [ ] Test `secured()` plugin with and without role requirements

## Security Considerations

### Token Security
- Access tokens SHOULD have short lifetimes (5-15 minutes)
- Refresh tokens SHOULD be used for long-lived sessions
- Tokens MUST be transmitted over HTTPS in production
- Tokens SHOULD NOT be logged or stored in localStorage

### Role and Scope Principle of Least Privilege
- Grant users only the roles and scopes they need
- Service clients should request minimal scopes
- Regularly audit role and scope assignments

### Audience Validation
- ALL services MUST validate audience claims
- Tokens without proper audience MUST be rejected
- Log audience validation failures for monitoring

### Rate Limiting
- Implement rate limiting on token endpoints
- Implement rate limiting on authenticated endpoints
- Consider separate limits for users vs. service clients

### Monitoring & Logging
- Log authentication failures with context
- Log authorization failures (scope/audience mismatches)
- Monitor for suspicious patterns (e.g., excessive failed attempts)
- DO NOT log tokens or secrets

## Future Considerations

### Admin Management System
When implementing the admin management UI:
- Use `secured("ADMIN")` for all admin routes
- Create dedicated admin scopes for each management area if fine-grained control is needed
- Implement fine-grained permissions within admin role
- Consider admin audit logging for compliance
- Implement admin session timeouts separate from user sessions

### BFF (Backend for Frontend)
When adding a public BFF:
- BFF will expose public menu endpoints (read-only)
- BFF handles authentication for user-specific operations
- BFF enforces rate limiting before forwarding to services
- BFF can aggregate multiple service calls

### Additional Services
When adding order service or other services:
- Follow the same audience and scope pattern
- Add service-specific scopes (e.g., `order`, `order:read`, `order:write`)
- Configure service clients for inter-service communication
- Update Keycloak realm configuration

### OpenID Connect Discovery
Consider using OIDC discovery for dynamic configuration:
```kotlin
val openIdConfig = httpClient.get("$issuer/.well-known/openid-configuration")
    .body<OpenIdConfiguration>()
```

This enables automatic updates when Keycloak endpoints change.

## References

- [OAuth 2.0 RFC 6749](https://tools.ietf.org/html/rfc6749)
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
- [JWT RFC 7519](https://tools.ietf.org/html/rfc7519)
- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Ktor Authentication](https://ktor.io/docs/authentication.html)

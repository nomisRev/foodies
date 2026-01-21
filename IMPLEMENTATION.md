# Service-to-Service Authentication Specification

## Overview

This specification defines the authentication mechanism for service-to-service (S2S) communication within the Foodies microservices architecture. Currently, services forward user JWT tokens for inter-service calls, which limits the ability to perform background operations, scheduled tasks, and event-driven workflows where no user context exists.

This specification introduces:
1. **OAuth2 Client Credentials flow** using Keycloak for service-to-service authentication
2. **CoroutineContext-based `AuthContext`** for implicit token propagation through coroutine scopes
3. **Type-safe routing DSL** (`secureUser`, `secureService`) with typed principals
4. **Automatic header injection** via Ktor client plugin based on coroutine context

This enables services to authenticate themselves independently of user context while maintaining security, auditability, and proper authorization boundaries.

## Problem Statement

### Current State

Services currently rely on user JWT token passthrough for inter-service communication:

```kotlin
// Current approach - requires user token
class HttpBasketClient(
    private val httpClient: HttpClient,
    private val baseUrl: String
) : BasketClient {
    override suspend fun getBasket(buyerId: String, token: String): CustomerBasket? {
        return httpClient.get("$baseUrl/basket") {
            header("Authorization", "Bearer $token")  // User's JWT
        }.body()
    }
}
```

### Limitations

| Limitation | Impact |
|------------|--------|
| No user context available | Background jobs and event handlers cannot make authenticated calls |
| Token expiration during long operations | Saga workflows may fail mid-execution |
| Coupling to user session | Services cannot perform autonomous operations |
| Limited audit trail | All actions attributed to users, not services |

### Target Scenarios

1. **Event-driven workflows**: Payment service processing orders without user context
2. **Background jobs**: Scheduled cleanup, data synchronization, report generation
3. **Service health checks**: Inter-service health verification
4. **Administrative operations**: Service-initiated data migrations or corrections

## Architecture

### Service Details

| Property | Value |
|----------|-------|
| Identity Provider | Keycloak |
| Auth Protocol | OAuth 2.0 Client Credentials Grant |
| Token Format | JWT (RS256) |
| Token Endpoint | `{keycloak}/realms/foodies/protocol/openid-connect/token` |
| JWKS Endpoint | `{keycloak}/realms/foodies/protocol/openid-connect/certs` |

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Service-to-Service Authentication Flow                    │
└─────────────────────────────────────────────────────────────────────────────┘

    ┌──────────────┐                                      ┌──────────────┐
    │   Order      │                                      │   Basket     │
    │   Service    │                                      │   Service    │
    └──────┬───────┘                                      └──────▲───────┘
           │                                                     │
           │ 1. Request service token                            │
           │    (client_credentials grant)                       │
           ▼                                                     │
    ┌──────────────┐                                             │
    │   Keycloak   │                                             │
    │   (IdP)      │                                             │
    └──────┬───────┘                                             │
           │                                                     │
           │ 2. Return service JWT                               │
           │    (contains service identity + roles)              │
           ▼                                                     │
    ┌──────────────┐                                             │
    │   Order      │  3. Call API with service token             │
    │   Service    │─────────────────────────────────────────────┤
    └──────────────┘    Authorization: Bearer <service-jwt>      │
                                                                 │
                        4. Validate JWT signature (JWKS)         │
                        5. Check service role/scope              │
                        6. Return response                       │
                                                                 ▼
```

### Token Flow Comparison

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   User Token Flow with AuthContext                           │
└─────────────────────────────────────────────────────────────────────────────┘

  User ──▶ WebApp ──▶ Order Service ──▶ Basket Service
               │              │                │
               │   User JWT   │    User JWT    │ (via AuthContext.UserAuth)
               └──────────────┴────────────────┘

  CoroutineContext: AuthContext.UserAuth(accessToken)
  HttpClient automatically injects: Authorization: Bearer <user-jwt>

┌─────────────────────────────────────────────────────────────────────────────┐
│                  Service Token Flow with AuthContext                         │
└─────────────────────────────────────────────────────────────────────────────┘

  Event ──▶ Order Service ──▶ Keycloak ──▶ Order Service ──▶ Basket Service
                    │              │              │                │
                    │  Credentials │ Service JWT  │  Service JWT   │
                    └──────────────┴──────────────┴────────────────┘

  CoroutineContext: AuthContext.ServiceAuth(serviceToken, userToken?)
  HttpClient automatically injects:
    - Authorization: Bearer <service-jwt>
    - X-User-Context: Bearer <user-jwt> (optional, for audit trail)
```

## Keycloak Configuration

### Service Client Registration

Each service requiring S2S authentication must be registered as a confidential client in Keycloak.

#### Client Configuration

| Setting | Value | Description |
|---------|-------|-------------|
| Client ID | `{service-name}-service` | e.g., `order-service`, `payment-service` |
| Client Protocol | `openid-connect` | Standard OIDC protocol |
| Access Type | `confidential` | Requires client secret |
| Service Account | `enabled` | Enables client credentials flow |
| Standard Flow | `disabled` | Not used for S2S |
| Direct Access | `disabled` | Not used for S2S |

#### Keycloak Terraform/Admin Configuration

```json
{
  "clientId": "order-service",
  "name": "Order Service",
  "description": "Backend service for order management",
  "enabled": true,
  "clientAuthenticatorType": "client-secret",
  "serviceAccountsEnabled": true,
  "standardFlowEnabled": false,
  "directAccessGrantsEnabled": false,
  "publicClient": false,
  "protocol": "openid-connect",
  "attributes": {
    "access.token.lifespan": "300"
  }
}
```

### Service Roles

Define roles that represent service-level permissions:

| Role | Description | Assigned To |
|------|-------------|-------------|
| `service:basket:read` | Read basket data | order-service |
| `service:basket:write` | Modify basket data | order-service |
| `service:menu:read` | Read menu items | basket-service, order-service |
| `service:order:read` | Read order data | payment-service |
| `service:payment:process` | Process payments | order-service |

### Role Mapping

```
┌─────────────────┐     ┌─────────────────────────────────────┐
│  order-service  │────▶│ service:basket:read                 │
│                 │     │ service:basket:write                │
│                 │     │ service:menu:read                   │
└─────────────────┘     └─────────────────────────────────────┘

┌─────────────────┐     ┌─────────────────────────────────────┐
│ payment-service │────▶│ service:order:read                  │
└─────────────────┘     └─────────────────────────────────────┘

┌─────────────────┐     ┌─────────────────────────────────────┐
│ basket-service  │────▶│ service:menu:read                   │
└─────────────────┘     └─────────────────────────────────────┘
```

## Implementation

### AuthContext (CoroutineContext-based Authentication)

Authentication context is propagated through coroutines using Kotlin's `CoroutineContext`, enabling automatic token injection for HTTP client calls without explicit parameter passing.

```kotlin
package io.ktor.foodies.server.auth

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

sealed interface AuthContext : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key

    data class UserAuth(
        val accessToken: String
    ) : AuthContext, AbstractCoroutineContextElement(Key) {
        override val key: CoroutineContext.Key<*> get() = AuthContext.Key
    }

    data class ServiceAuth(
        val serviceToken: String,
        val userToken: String? = null
    ) : AuthContext, AbstractCoroutineContextElement(Key) {
        override val key: CoroutineContext.Key<*> get() = AuthContext.Key
    }

    companion object Key : CoroutineContext.Key<AuthContext>
}
```

### AuthContext Client Plugin

The client plugin automatically injects authentication headers based on the current coroutine context:

```kotlin
package io.ktor.foodies.server.auth

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.currentCoroutineContext

val AuthContextPlugin = createClientPlugin("AuthContext") {
    onRequest { request, _ ->
        when (val auth = currentCoroutineContext()[AuthContext]) {
            is AuthContext.UserAuth -> {
                request.header(HttpHeaders.Authorization, "Bearer ${auth.accessToken}")
            }
            is AuthContext.ServiceAuth -> {
                request.header(HttpHeaders.Authorization, "Bearer ${auth.serviceToken}")
                auth.userToken?.let { request.header("X-User-Context", "Bearer $it") }
            }
            null -> {}
        }
    }
}
```

### Token Provider Interface

```kotlin
package io.ktor.foodies.server.auth

import kotlinx.datetime.Instant

data class ServiceToken(
    val accessToken: String,
    val expiresAt: Instant,
    val tokenType: String = "Bearer"
) {
    fun isExpired(bufferSeconds: Long = 30): Boolean =
        Clock.System.now() >= expiresAt - bufferSeconds.seconds
}

interface ServiceTokenProvider {
    suspend fun getToken(): ServiceToken

    suspend fun <T> withServiceAuth(
        userToken: String? = null,
        block: suspend () -> T
    ): T {
        val token = getToken()
        return withContext(AuthContext.ServiceAuth(token.accessToken, userToken)) {
            block()
        }
    }
}
```

### Client Credentials Token Provider

```kotlin
package io.ktor.foodies.server.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.parameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

@Serializable
data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("token_type")
    val tokenType: String
)

class ClientCredentialsTokenProvider(
    private val httpClient: HttpClient,
    private val tokenEndpoint: String,
    private val clientId: String,
    private val clientSecret: String
) : ServiceTokenProvider {

    private var cachedToken: ServiceToken? = null
    private val mutex = Mutex()

    override suspend fun getToken(): ServiceToken = mutex.withLock {
        cachedToken?.takeUnless { it.isExpired() } ?: fetchNewToken().also { cachedToken = it }
    }

    private suspend fun fetchNewToken(): ServiceToken {
        val response: TokenResponse = httpClient.submitForm(
            url = tokenEndpoint,
            formParameters = parameters {
                append("grant_type", "client_credentials")
                append("client_id", clientId)
                append("client_secret", clientSecret)
            }
        ).body()

        return ServiceToken(
            accessToken = response.accessToken,
            expiresAt = Clock.System.now() + response.expiresIn.seconds,
            tokenType = response.tokenType
        )
    }
}
```

### Service Client with AuthContext

Service clients no longer need explicit token parameters. The `AuthContextPlugin` automatically injects headers from the coroutine context:

```kotlin
package io.ktor.foodies.order.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.foodies.server.auth.AuthContextPlugin
import io.ktor.http.HttpStatusCode

interface BasketClient {
    suspend fun getBasket(buyerId: String): CustomerBasket?
}

class HttpBasketClient(
    private val httpClient: HttpClient,
    private val baseUrl: String
) : BasketClient {

    private val basketBaseUrl = baseUrl.trimEnd('/')

    override suspend fun getBasket(buyerId: String): CustomerBasket? {
        return try {
            httpClient.get("$basketBaseUrl/basket/$buyerId").body<CustomerBasket>()
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) null
            else throw e
        }
    }
}

fun createAuthenticatedHttpClient(): HttpClient = HttpClient {
    install(AuthContextPlugin)
    install(ContentNegotiation) {
        json()
    }
}
```

### Usage Examples

```kotlin
// User-initiated request (token from request context)
withContext(AuthContext.UserAuth(userJwt)) {
    basketClient.getBasket(userId)
}

// Service-to-service call (background job, event handler)
tokenProvider.withServiceAuth {
    basketClient.getBasket(userId)
}

// Service call with user context preserved (for audit trail)
tokenProvider.withServiceAuth(userToken = originalUserJwt) {
    basketClient.getBasket(userId)
}
```

### Principal Types

Type-safe principal classes for user and service authentication:

```kotlin
package io.ktor.foodies.server.auth

import io.ktor.server.auth.Principal

data class UserPrincipal(
    val userId: String,
    val email: String?,
    val roles: Set<String>,
    val accessToken: String
) : Principal

data class ServicePrincipal(
    val serviceAccountId: String,
    val clientId: String,
    val roles: Set<String>,
    val userContext: UserPrincipal? = null
) : Principal
```

### JWT Validation with Typed Principals

```kotlin
package io.ktor.foodies.server.openid

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.foodies.server.auth.UserPrincipal
import io.ktor.foodies.server.auth.ServicePrincipal

suspend fun Application.security(auth: Auth, client: HttpClient) {
    val config = client.use { it.discover(auth.issuer) }

    install(Authentication) {
        jwt("user") {
            verifier(config.jwks(), config.issuer) {
                withAudience("foodies")
            }
            validate { credential ->
                val payload = credential.payload
                val email = payload.getClaim("email").asString()
                if (email != null) {
                    val roles = payload.getClaim("realm_access")
                        ?.asMap()?.get("roles") as? List<*> ?: emptyList<String>()
                    UserPrincipal(
                        userId = payload.subject,
                        email = email,
                        roles = roles.filterIsInstance<String>().toSet(),
                        accessToken = credential.token
                    )
                } else null
            }
        }

        jwt("service") {
            verifier(config.jwks(), config.issuer) {
                withAudience("foodies")
            }
            validate { credential ->
                val payload = credential.payload
                val clientId = payload.getClaim("azp").asString()
                    ?: payload.getClaim("client_id").asString()
                if (clientId?.endsWith("-service") == true) {
                    val roles = payload.getClaim("realm_access")
                        ?.asMap()?.get("roles") as? List<*> ?: emptyList<String>()
                    ServicePrincipal(
                        serviceAccountId = payload.subject,
                        clientId = clientId,
                        roles = roles.filterIsInstance<String>().toSet()
                    )
                } else null
            }
        }
    }
}
```

### Secure Routing DSL

Type-safe routing DSL that automatically installs `AuthContext` into the coroutine context for downstream HTTP client calls. The DSL intercepts route handling to establish the authentication context before any route handlers execute.

```kotlin
package io.ktor.foodies.server.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.coroutines.withContext

fun Route.secureUser(build: Route.() -> Unit): Route {
    return authenticate("user") {
        intercept(ApplicationCallPipeline.Call) {
            val principal = call.principal<UserPrincipal>()
                ?: return@intercept call.respond(HttpStatusCode.Unauthorized)
            withContext(AuthContext.UserAuth(principal.accessToken)) {
                proceed()
            }
        }
        build()
    }
}

fun Route.secureService(
    vararg requiredRoles: String,
    build: Route.() -> Unit
): Route {
    return authenticate("service") {
        intercept(ApplicationCallPipeline.Call) {
            val principal = call.principal<ServicePrincipal>()
                ?: return@intercept call.respond(HttpStatusCode.Unauthorized)

            if (requiredRoles.isNotEmpty()) {
                val missingRoles = requiredRoles.filter { it !in principal.roles }
                if (missingRoles.isNotEmpty()) {
                    return@intercept call.respond(HttpStatusCode.Forbidden)
                }
            }

            val userContextToken = call.request.headers["X-User-Context"]?.removePrefix("Bearer ")
            val serviceToken = call.request.headers["Authorization"]!!.removePrefix("Bearer ")
            val authContext = AuthContext.ServiceAuth(serviceToken, userContextToken)

            withContext(authContext) {
                proceed()
            }
        }
        build()
    }
}

fun ApplicationCall.userPrincipal(): UserPrincipal =
    principal<UserPrincipal>()
        ?: error("UserPrincipal not found - route not properly secured with secureUser")

fun ApplicationCall.servicePrincipal(): ServicePrincipal =
    principal<ServicePrincipal>()
        ?: error("ServicePrincipal not found - route not properly secured with secureService")
```

### Route Authorization with Typed DSL

```kotlin
package io.ktor.foodies.basket

import io.ktor.foodies.server.auth.secureUser
import io.ktor.foodies.server.auth.secureService
import io.ktor.foodies.server.auth.userPrincipal
import io.ktor.foodies.server.auth.servicePrincipal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.basketRoutes(basketService: BasketService) {
    route("/basket") {
        secureUser {
            get {
                val user = call.userPrincipal()
                val basket = basketService.getBasket(user.userId)
                call.respond(basket ?: HttpStatusCode.NotFound)
            }
        }

        secureService("service:basket:read") {
            get("/{buyerId}") {
                val buyerId = call.parameters["buyerId"]!!
                val basket = basketService.getBasket(buyerId)
                call.respond(basket ?: HttpStatusCode.NotFound)
            }
        }
    }
}
```

### Comparison: Before and After

**Before (raw JWT handling):**
```kotlin
authenticate("user") {
    get {
        val principal = call.principal<JWTPrincipal>()!!
        val buyerId = principal.subject!!  // Nullable, can fail
        val roles = principal.payload.getClaim("realm_access")
            ?.asMap()?.get("roles") as? List<*> ?: emptyList<String>()
        // Manual role checking, manual token extraction for downstream calls
    }
}
```

**After (typed DSL):**
```kotlin
secureUser {
    get {
        val user = call.userPrincipal()  // Type-safe, non-null
        val buyerId = user.userId        // Direct access
        // AuthContext automatically available for downstream HTTP calls
    }
}

// Service routes with role-based authorization
secureService("service:basket:read", "service:basket:write") {
    post("/{buyerId}") {
        val service = call.servicePrincipal()
        // Roles already validated by DSL, AuthContext established
    }
}
```

## Configuration

### Application Configuration

```yaml
# application.yaml
auth:
  issuer: ${AUTH_ISSUER:http://keycloak:8080/realms/foodies}
  audience: foodies

serviceAuth:
  clientId: ${SERVICE_CLIENT_ID:order-service}
  clientSecret: ${SERVICE_CLIENT_SECRET}
  tokenEndpoint: ${AUTH_ISSUER:http://keycloak:8080}/realms/foodies/protocol/openid-connect/token
```

### Configuration Data Class

```kotlin
package io.ktor.foodies.server.config

import kotlinx.serialization.Serializable

@Serializable
data class ServiceAuthConfig(
    val clientId: String,
    val clientSecret: String,
    val tokenEndpoint: String
)
```

### Kubernetes Secrets

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: order-service-credentials
  namespace: foodies
type: Opaque
stringData:
  SERVICE_CLIENT_ID: order-service
  SERVICE_CLIENT_SECRET: <generated-secret>
```

### Deployment Configuration

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
  namespace: foodies
spec:
  template:
    spec:
      containers:
        - name: order-service
          env:
            - name: SERVICE_CLIENT_ID
              valueFrom:
                secretKeyRef:
                  name: order-service-credentials
                  key: SERVICE_CLIENT_ID
            - name: SERVICE_CLIENT_SECRET
              valueFrom:
                secretKeyRef:
                  name: order-service-credentials
                  key: SERVICE_CLIENT_SECRET
```

## Service Token Claims

### Example Service JWT Payload

```json
{
  "exp": 1705689600,
  "iat": 1705689300,
  "jti": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "iss": "http://keycloak:8080/realms/foodies",
  "aud": "foodies",
  "sub": "service-account-order-service",
  "typ": "Bearer",
  "azp": "order-service",
  "acr": "1",
  "realm_access": {
    "roles": [
      "service:basket:read",
      "service:basket:write",
      "service:menu:read"
    ]
  },
  "scope": "openid profile",
  "clientId": "order-service",
  "clientHost": "10.244.0.15",
  "clientAddress": "10.244.0.15"
}
```

### Token Claim Reference

| Claim | Description | Example |
|-------|-------------|---------|
| `sub` | Service account identifier | `service-account-order-service` |
| `azp` | Authorized party (client ID) | `order-service` |
| `clientId` | Client identifier | `order-service` |
| `realm_access.roles` | Assigned service roles | `["service:basket:read"]` |
| `exp` | Token expiration (Unix timestamp) | `1705689600` |

## Security Considerations

### Token Lifetime

| Token Type | Lifetime | Rationale |
|------------|----------|-----------|
| Service tokens | 5 minutes | Short-lived to limit exposure if compromised |
| Refresh buffer | 30 seconds | Proactive refresh before expiration |

### Secret Management

1. **Never hardcode secrets**: Use environment variables or secret management
2. **Rotate secrets regularly**: Implement secret rotation strategy
3. **Audit access**: Log all token acquisitions and API calls
4. **Separate secrets per environment**: Different credentials for dev/staging/prod

### Network Security

1. **mTLS between services**: Consider adding mutual TLS for additional security
2. **Network policies**: Restrict which services can communicate
3. **Rate limiting**: Prevent token endpoint abuse

### Logging and Auditing

```kotlin
class AuditingTokenProvider(
    private val delegate: ServiceTokenProvider,
    private val serviceName: String
) : ServiceTokenProvider {

    override suspend fun getToken(): ServiceToken {
        return delegate.getToken().also { token ->
            logger.info {
                "Service token acquired" to mapOf(
                    "service" to serviceName,
                    "expiresAt" to token.expiresAt.toString()
                )
            }
        }
    }
}
```

## Testing Strategy

### Unit Tests for AuthContext

```kotlin
class AuthContextPluginTest {
    @Test
    fun `should inject user token from coroutine context`() = runTest {
        val capturedHeaders = mutableMapOf<String, String>()
        val client = HttpClient(MockEngine) {
            install(AuthContextPlugin)
            engine {
                addHandler { request ->
                    request.headers.forEach { name, values ->
                        capturedHeaders[name] = values.first()
                    }
                    respond("")
                }
            }
        }

        withContext(AuthContext.UserAuth("user-jwt-token")) {
            client.get("http://example.com/api")
        }

        assertEquals("Bearer user-jwt-token", capturedHeaders["Authorization"])
    }

    @Test
    fun `should inject service token with user context`() = runTest {
        val capturedHeaders = mutableMapOf<String, String>()
        val client = HttpClient(MockEngine) {
            install(AuthContextPlugin)
            engine {
                addHandler { request ->
                    request.headers.forEach { name, values ->
                        capturedHeaders[name] = values.first()
                    }
                    respond("")
                }
            }
        }

        withContext(AuthContext.ServiceAuth("service-jwt", "original-user-jwt")) {
            client.get("http://example.com/api")
        }

        assertEquals("Bearer service-jwt", capturedHeaders["Authorization"])
        assertEquals("Bearer original-user-jwt", capturedHeaders["X-User-Context"])
    }

    @Test
    fun `should not inject headers when no auth context present`() = runTest {
        val capturedHeaders = mutableMapOf<String, String>()
        val client = HttpClient(MockEngine) {
            install(AuthContextPlugin)
            engine {
                addHandler { request ->
                    request.headers.forEach { name, values ->
                        capturedHeaders[name] = values.first()
                    }
                    respond("")
                }
            }
        }

        client.get("http://example.com/api")

        assertFalse(capturedHeaders.containsKey("Authorization"))
    }
}
```

### Unit Tests for Token Provider

```kotlin
class ClientCredentialsTokenProviderTest {
    @Test
    fun `should cache token until expiration`() = runTest {
        val mockClient = createMockHttpClient(tokenLifetimeSeconds = 300)
        val provider = ClientCredentialsTokenProvider(
            httpClient = mockClient,
            tokenEndpoint = "http://keycloak/token",
            clientId = "test-service",
            clientSecret = "secret"
        )

        val token1 = provider.getToken()
        val token2 = provider.getToken()

        assertEquals(token1.accessToken, token2.accessToken)
        assertEquals(1, mockClient.requestCount)
    }

    @Test
    fun `should refresh token when expired`() = runTest {
        val mockClient = createMockHttpClient(tokenLifetimeSeconds = 1)
        val provider = ClientCredentialsTokenProvider(...)

        val token1 = provider.getToken()
        advanceTimeBy(2.seconds)
        val token2 = provider.getToken()

        assertNotEquals(token1.accessToken, token2.accessToken)
    }

    @Test
    fun `withServiceAuth should establish auth context`() = runTest {
        val provider = ClientCredentialsTokenProvider(...)
        var capturedContext: AuthContext? = null

        provider.withServiceAuth {
            capturedContext = currentCoroutineContext()[AuthContext]
        }

        assertIs<AuthContext.ServiceAuth>(capturedContext)
        assertNotNull((capturedContext as AuthContext.ServiceAuth).serviceToken)
    }
}
```

### Integration Tests

```kotlin
class ServiceToServiceAuthIntegrationTest {
    @Container
    val keycloak = KeycloakContainer("quay.io/keycloak/keycloak:26.0")
        .withRealmImportFile("test-realm.json")

    @Test
    fun `order service can call basket service with service token`() = testApplication {
        val tokenProvider = ClientCredentialsTokenProvider(
            httpClient = createClient { install(ContentNegotiation) { json() } },
            tokenEndpoint = keycloak.authServerUrl + "/realms/foodies/protocol/openid-connect/token",
            clientId = "order-service",
            clientSecret = "test-secret"
        )

        val basketClient = HttpBasketClient(
            httpClient = createClient {
                install(AuthContextPlugin)
                install(ContentNegotiation) { json() }
            },
            baseUrl = basketServiceUrl
        )

        tokenProvider.withServiceAuth {
            val basket = basketClient.getBasket("user-123")
            assertNotNull(basket)
        }
    }

    @Test
    fun `service call with user context preserves audit trail`() = testApplication {
        val tokenProvider = ClientCredentialsTokenProvider(...)
        val basketClient = HttpBasketClient(...)
        var receivedUserContext: String? = null

        // Set up basket service to capture X-User-Context header
        routing {
            secureService {
                get("/basket/{buyerId}") {
                    withServiceContext {
                        receivedUserContext = call.request.headers["X-User-Context"]
                        call.respond(CustomerBasket(...))
                    }
                }
            }
        }

        tokenProvider.withServiceAuth(userToken = "original-user-jwt") {
            basketClient.getBasket("user-123")
        }

        assertEquals("Bearer original-user-jwt", receivedUserContext)
    }
}
```

### Route DSL Tests

```kotlin
class SecureRoutingDslTest {
    @Test
    fun `secureUser routes provide typed principal and AuthContext`() = testApplication {
        application {
            install(Authentication) { /* configure jwt */ }
            routing {
                secureUser {
                    get("/me") {
                        val user = call.userPrincipal()
                        call.respond(mapOf("userId" to user.userId))
                    }
                }
            }
        }

        val response = client.get("/me") {
            header("Authorization", "Bearer $validUserJwt")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `secureService routes enforce role requirements at DSL level`() = testApplication {
        application {
            install(Authentication) { /* configure jwt */ }
            routing {
                secureService("service:admin:write") {
                    get("/admin") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }

        val response = client.get("/admin") {
            header("Authorization", "Bearer $serviceJwtWithoutAdminRole")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `secureService with multiple required roles`() = testApplication {
        application {
            install(Authentication) { /* configure jwt */ }
            routing {
                secureService("service:basket:read", "service:basket:write") {
                    post("/basket/{id}") {
                        val service = call.servicePrincipal()
                        call.respond(mapOf("clientId" to service.clientId))
                    }
                }
            }
        }

        // Service with only read role should be forbidden
        val response = client.post("/basket/123") {
            header("Authorization", "Bearer $serviceJwtWithOnlyReadRole")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `secureService without required roles allows any authenticated service`() = testApplication {
        application {
            install(Authentication) { /* configure jwt */ }
            routing {
                secureService {
                    get("/health") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }

        val response = client.get("/health") {
            header("Authorization", "Bearer $anyValidServiceJwt")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }
}
```

## Migration Strategy

### Phase 1: Infrastructure Setup

1. Register service clients in Keycloak
2. Configure service roles and role mappings
3. Deploy Kubernetes secrets

### Phase 2: Service Updates

1. Add `ServiceTokenProvider` to `server-shared` module
2. Update service clients to accept both user and service tokens
3. Add new authenticated routes for service-to-service calls

### Phase 3: Gradual Rollout

1. Enable S2S auth for event handlers first
2. Migrate background jobs
3. Update existing user-context calls where appropriate

### Phase 4: Cleanup

1. Remove unused user-token passthrough where replaced
2. Update documentation
3. Security audit

## Dependencies

### build.gradle.kts additions

```kotlin
// server-shared module
dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.datetime)
}
```

## API Summary

### New Service Endpoints

| Service | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| Basket | `GET /basket/{buyerId}` | service | Get basket by buyer ID |
| Menu | `GET /menu/items/{id}` | service | Get menu item by ID |
| Order | `GET /orders/{orderId}` | service | Get order details |

### Core Components

| Component | Description |
|-----------|-------------|
| `AuthContext` | Sealed interface for coroutine context-based auth propagation |
| `AuthContext.UserAuth` | User authentication context with access token |
| `AuthContext.ServiceAuth` | Service authentication context with optional user context |
| `AuthContextPlugin` | Ktor client plugin for automatic header injection |
| `UserPrincipal` | Type-safe user principal with userId, email, roles |
| `ServicePrincipal` | Type-safe service principal with clientId, roles |

### Routing DSL Functions

| Function | Description |
|----------|-------------|
| `secureUser { }` | Creates route requiring user authentication, establishes `AuthContext.UserAuth` |
| `secureService(vararg roles) { }` | Creates route requiring service authentication with optional role enforcement, establishes `AuthContext.ServiceAuth` |
| `call.userPrincipal()` | Returns typed UserPrincipal (non-null) within `secureUser` routes |
| `call.servicePrincipal()` | Returns typed ServicePrincipal (non-null) within `secureService` routes |

### Token Provider Methods

| Method | Description |
|--------|-------------|
| `getToken()` | Returns cached or fresh service token |
| `withServiceAuth { }` | Executes block with ServiceAuth context |
| `withServiceAuth(userToken) { }` | Executes with ServiceAuth preserving user context |

## Testing OpenTelemetry Nocode for Keycloak User Registration Flow

This section describes how to test the end-to-end distributed tracing for the Keycloak user registration flow using OpenTelemetry's automatic instrumentation (nocode/javaagent).

### Architecture Overview

The user registration flow involves multiple components that should be traced:

```
User Registration → Keycloak → RabbitMQ → Profile Service → PostgreSQL
                       │           │            │              │
                       └───────────┴────────────┴──────────────┘
                              OpenTelemetry Traces
```

### Components in the Registration Flow

1. **Keycloak**: User registration event (REGISTER event type)
2. **Keycloak RabbitMQ Publisher**: Transforms event to `UserEvent.Registration` and publishes to queue
3. **RabbitMQ**: Message broker with queue `profile.registration`
4. **Profile Service**: Consumes registration event and persists to database
5. **PostgreSQL**: Stores user profile data

### OpenTelemetry Instrumentation

The application uses both manual and automatic instrumentation:

#### Manual Instrumentation (Existing)

Located in `server-shared/src/main/kotlin/io/ktor/foodies/server/telemetry/OpenTelemetry.kt`:

- **Ktor Server**: `KtorServerTelemetry` plugin for HTTP request tracing
- **OTLP Exporters**: Configured for traces and metrics export
- **W3C Trace Context Propagation**: Standard trace context propagation across services

```kotlin
val openTelemetry = OpenTelemetrySdk.builder()
    .setTracerProvider(tracerProvider)
    .setMeterProvider(meterProvider)
    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
    .buildAndRegisterGlobal()
```

#### Automatic Instrumentation (Nocode/JavaAgent)

The OpenTelemetry Java agent provides automatic instrumentation for:

- **JDBC/PostgreSQL**: Database queries and connections (via `opentelemetry-jdbc`)
- **HikariCP**: Connection pool metrics (via `opentelemetry-hikaricp-3.0`)
- **RabbitMQ**: Message publishing and consumption
- **HTTP Clients**: Outbound HTTP calls

Service name and version are configured via environment variables:
- `OTEL_SERVICE_NAME`: Identifies the service in traces
- `OTEL_RESOURCE_ATTRIBUTES`: Additional resource attributes (e.g., `service.version=0.0.4`)

### Testing the Registration Flow

#### Prerequisites

1. **Deploy the full stack** to Kubernetes:
   ```bash
   ./gradlew publishImageToLocalRegistry
   kubectl apply -k k8s/base
   ```

2. **Verify OTEL Collector** is running:
   ```bash
   kubectl get pods -n foodies | grep otel-collector
   kubectl logs -n foodies <otel-collector-pod> -f
   ```

3. **Access services**:
   - Keycloak: `http://foodies.local/auth` (via Ingress)
   - Jaeger UI: Port-forward to view traces
     ```bash
     kubectl port-forward -n foodies svc/jaeger 16686:16686
     ```
     Access at `http://localhost:16686`

#### Test Procedure

1. **Register a new user** via Keycloak:
   - Navigate to `http://foodies.local/auth/realms/foodies/account`
   - Complete user registration form with:
     - Email: `test@example.com`
     - First Name: `Test`
     - Last Name: `User`

2. **Verify event flow** through logs:
   ```bash
   # Keycloak publisher logs
   kubectl logs -n foodies <keycloak-pod> | grep "profile-webhook"

   # RabbitMQ queue status
   kubectl port-forward -n foodies svc/rabbitmq 15672:15672
   # Access http://localhost:15672, login (guest/guest)
   # Check queue: profile.registration

   # Profile service logs
   kubectl logs -n foodies -l app=profile --tail=100
   ```

3. **Inspect distributed trace** in Jaeger:
   - Open Jaeger UI: `http://localhost:16686`
   - Select service: `profile` (from dropdown)
   - Search for traces containing operation: `profile.registration consume`
   - Expected trace spans:

     ```
     profile.registration consume                    [RabbitMQ Consumer]
     ├── INSERT INTO profiles                        [JDBC/PostgreSQL]
     │   └── HikariCP.getConnection                  [Connection Pool]
     └── profile.registration.ack                    [RabbitMQ ACK]
     ```

4. **Verify trace context propagation**:
   - Trace ID should be consistent across all spans
   - Parent-child relationships should be correctly established
   - Service name should appear as `profile` (from `OTEL_SERVICE_NAME`)
   - Database queries should include statement details (parameterized)

5. **Check database insertion**:
   ```bash
   kubectl port-forward -n foodies svc/postgres 5432:5432
   psql -h localhost -U foodies_admin -d foodies-profile-database \
     -c "SELECT * FROM profiles ORDER BY id DESC LIMIT 1;"
   ```

#### Expected Trace Attributes

**RabbitMQ Consumer Span**:
- `messaging.system`: `rabbitmq`
- `messaging.destination.name`: `profile.registration`
- `messaging.operation.type`: `receive`
- `messaging.message.id`: Message ID
- `messaging.rabbitmq.routing_key`: Routing key

**Database Query Span**:
- `db.system`: `postgresql`
- `db.name`: `foodies-profile-database`
- `db.statement`: SQL statement (may be parameterized)
- `db.operation.name`: `INSERT`
- `db.sql.table`: `profiles`

**HikariCP Span**:
- `pool.name`: `HikariPool-1`
- `db.pool.operation`: `acquire`

#### Troubleshooting

**No traces appearing in Jaeger**:
1. Verify OTEL Collector is receiving spans:
   ```bash
   kubectl logs -n foodies <otel-collector-pod> | grep -i "trace"
   ```

2. Check Profile service environment variables:
   ```bash
   kubectl get deployment profile -n foodies -o yaml | grep -A5 OTEL
   ```

   Should include:
   ```yaml
   - name: OTEL_EXPORTER_OTLP_ENDPOINT
     value: http://otel-collector:4317
   ```

3. Verify OpenTelemetry SDK initialization in service logs:
   ```bash
   kubectl logs -n foodies -l app=profile | grep -i "opentelemetry"
   ```

**Missing database spans**:
- Ensure `opentelemetry-jdbc` instrumentation is active
- Check that HikariCP is wrapped with JDBC instrumentation
- Verify database URL includes required parameters

**RabbitMQ spans missing**:
- Confirm RabbitMQ client library version supports automatic instrumentation
- Check that trace context is properly propagated in message headers
- Verify consumer is using instrumented RabbitMQ client

#### Additional Testing Scenarios

1. **Profile Update Flow**:
   - Update user profile in Keycloak
   - Verify `UserEvent.UpdateProfile` trace shows upsert operation

2. **Account Deletion Flow**:
   - Delete user account
   - Verify `UserEvent.Delete` trace shows DELETE operation

3. **Error Scenarios**:
   - Invalid message format → Check error span attributes
   - Database constraint violation → Verify exception details in span
   - RabbitMQ connection failure → Check retry traces

4. **Performance Analysis**:
   - Measure end-to-end latency from event publish to database commit
   - Identify bottlenecks in trace flamegraph
   - Analyze connection pool wait times

### Configuration Reference

#### Profile Service Deployment

OpenTelemetry configuration in `k8s/base/profile/deployment.yaml`:

```yaml
env:
  - name: OTEL_EXPORTER_OTLP_ENDPOINT
    valueFrom:
      configMapKeyRef:
        name: foodies-config
        key: OTEL_EXPORTER_OTLP_ENDPOINT
```

Additional environment variables for automatic instrumentation (if using Java agent):

```yaml
env:
  - name: OTEL_SERVICE_NAME
    value: profile
  - name: OTEL_RESOURCE_ATTRIBUTES
    value: service.version=0.0.4,deployment.environment=dev
  - name: OTEL_TRACES_EXPORTER
    value: otlp
  - name: OTEL_METRICS_EXPORTER
    value: otlp
  - name: OTEL_EXPORTER_OTLP_PROTOCOL
    value: grpc
```

#### Application Configuration

From `profile/src/main/resources/application.yaml`:

```yaml
monitoring:
  otlpEndpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}
```

### Benefits of Nocode Instrumentation

1. **Zero code changes**: Automatic instrumentation via Java agent
2. **Comprehensive coverage**: Database, messaging, HTTP automatically traced
3. **Consistent metadata**: Service name, version, environment automatically added
4. **Performance overhead**: Minimal impact with sampling strategies
5. **Standards compliance**: W3C Trace Context for cross-service correlation

## Future Enhancements

1. ~~**Token exchange**: Support for exchanging user tokens to service tokens with user context~~ ✅ Implemented via `X-User-Context` header
2. **Scope-based authorization**: Fine-grained permissions using OAuth scopes
3. **Service mesh integration**: Integrate with Istio/Linkerd for automatic mTLS
4. **Centralized policy engine**: OPA (Open Policy Agent) for complex authorization rules
5. **Token revocation**: Support for immediate token invalidation

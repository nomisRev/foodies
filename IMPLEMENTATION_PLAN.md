# Authentication Implementation Plan

**Epic**: bd-2g4 - Authentication Specification: User Flow & Service-to-Service Authentication

This document provides a task-oriented view of implementing authentication best practices for the Foodies microservices platform.

## Overview

The implementation follows a phased approach to introduce service-to-service authentication while maintaining backward compatibility with existing user authentication flows. All tasks are tracked in `br` issue tracker.

## Phase 1: Foundation (P1 - Critical Path)

### Infrastructure Setup

**bd-3r2**: Define service principals and scopes in Keycloak
- Register each microservice as a Keycloak client
- Configure service accounts with client credentials
- Define OAuth2 scopes: `basket:items:read`, `basket:items:write`, `order:orders:read`, etc.
- Set access token lifespan to 15 minutes

**bd-3vg**: Add Kubernetes secrets for service credentials
- Create Secret manifests for each service
- Store `client-id` and `client-secret` per service
- Update deployment manifests to inject credentials as env vars
- Document secret rotation procedures

### Core Library Components (server-shared)

**bd-3nx**: Create result type definitions
- Create `AuthResult.kt` with sealed interface for authentication results
- Create `AuthorizationResult.kt` for authorization decisions
- Create `TokenResult.kt` for token acquisition results
- Create `JwtVerifyResult.kt` for JWT validation results
- All result types use sealed interfaces (no exceptions for control flow)
- Location: `server-shared/src/main/kotlin/io/ktor/foodies/server/openid/`

**bd-1od**: Implement ServiceTokenProvider for OAuth2 client credentials flow
- Create `ServiceTokenProvider` class for token acquisition
- Implement OAuth2 client credentials grant flow
- Return `TokenResult` instead of throwing exceptions
- Add token caching with automatic refresh
- Support per-target-service tokens with different audiences
- Location: `server-shared/src/main/kotlin/io/ktor/foodies/server/openid/ServiceTokenProvider.kt`

**bd-1ln**: Enhance AuthContext to support dual-token pattern
- Convert `AuthContext` to sealed interface with `UserAuth` and `ServiceAuth` variants
- Update `AuthContextPlugin` to handle both auth types
- Set headers based on auth type:
  - UserAuth: `Authorization: Bearer {accessToken}`
  - ServiceAuth: `Authorization: Bearer {serviceToken}`, `X-User-Context: {userToken?}`
- Maintain backward compatibility during migration
- Location: `server-shared/src/main/kotlin/io/ktor/foodies/server/openid/AuthContext.kt`

**bd-2qa**: Implement ServiceAuthContext and validation logic
- Create `ServiceAuthContext` data class with service and user principal info
- Implement JWT validation returning `AuthResult<ServiceAuthContext>`
- Extract service identity, scopes, and user principal
- Support legacy user-token-only requests during migration
- No exceptions for control flow - use result types
- Location: `server-shared/src/main/kotlin/io/ktor/foodies/server/openid/ServiceAuthContext.kt`

**bd-rzz**: Create composable DSL for service authorization
- Create `ServiceAuthScope` interface for context receiver pattern
- Implement `withServiceAuth()` route extension
- Create authorization helpers with context receivers:
  - `requireScope()` returning `AuthorizationResult`
  - `requireResourceOwnership()` returning `AuthorizationResult`
- Support configurable enforcement mode
- Pattern similar to existing `UserSessionScope`
- Location: `server-shared/src/main/kotlin/io/ktor/foodies/server/openid/ServiceAuthScope.kt`
- Location: `server-shared/src/main/kotlin/io/ktor/foodies/server/openid/Authorization.kt`

## Phase 2: Service Migration (P2 - Sequential Rollout)

### WebApp (Gateway Service)

**bd-1w3**: Update WebApp to use service-to-service authentication
- Acquire service tokens on startup using `ServiceTokenProvider`
- Handle `TokenResult` appropriately (retry on failure, log errors)
- Update `BasketService` HTTP client to use `AuthContext.ServiceAuth`
- Update `MenuService` HTTP client similarly
- Propagate user tokens from `UserSession` to service auth context
- Update `UserSessionScope` to use `AuthContext.UserAuth` (backward compatible)
- Test end-to-end user flow with new auth pattern

### Backend Services

**bd-1sq**: Update Basket service with service authentication
- Use `withServiceAuth` DSL to wrap routes
- Add scope validation using `requireScope()` with result types:
  - `GET /basket`: requires `basket:items:read`
  - `POST /basket/items`: requires `basket:items:write`
  - `DELETE /basket/items/{id}`: requires `basket:items:write`
- Update `MenuService` HTTP client to use `AuthContext.ServiceAuth`
- Implement resource ownership using `requireResourceOwnership()` with result types
- Handle `AuthorizationResult` in when expressions (no exceptions)

**bd-39x**: Update Menu service with service authentication
- Use `withServiceAuth` DSL to wrap routes
- Add scope validation using `requireScope()`:
  - `GET /menu/items`: requires `menu:items:read`
- Menu is read-only; focus on service identity verification
- No downstream service calls to update
- Handle authorization results properly

**bd-3pl**: Update Order service with service authentication
- Use `withServiceAuth` DSL to wrap routes
- Add scope validation using `requireScope()`:
  - `GET /orders`: requires `order:orders:read`
  - `POST /orders`: requires `order:orders:write`
  - `GET /orders/{id}`: requires `order:orders:read`
- Update `BasketClient` to use `AuthContext.ServiceAuth`
- Integrate composable auth with existing patterns
- Use `requireResourceOwnership()` for order ownership checks
- Return proper result types (consistent with existing `GetOrderResult` pattern)

**bd-3u0**: Update Profile service with service authentication
- Use `withServiceAuth` DSL if HTTP endpoints exist
- Add scope validation with result types
- Profile service is event-driven (RabbitMQ); document auth patterns for events
- No downstream HTTP service calls currently

**bd-13w**: Update Payment service with service authentication
- Use `withServiceAuth` DSL to wrap routes
- Add scope validation using `requireScope()`:
  - `POST /payments`: requires `payment:transactions:initiate`
- Payment service is event-driven; focus on HTTP endpoints
- Ensure proper service identity in event handlers
- Consistent with existing `PaymentResult` pattern

### User Authentication Enhancements

**bd-vdu**: Implement PKCE for OAuth2 authorization code flow
- Enhance WebApp OAuth2 flow with PKCE
- Generate `code_verifier` (random string)
- Generate `code_challenge` (SHA256 hash of verifier)
- Send `code_challenge` and `code_challenge_method` in authorization request
- Send `code_verifier` in token exchange
- Prevents authorization code interception attacks
- Location: `webapp/src/main/kotlin/io/ktor/foodies/server/security/Security.kt`

**bd-3vs**: Implement proactive token refresh strategy
- Add token refresh logic to `UserSessionScope`
- Check token expiration on session access
- Refresh if < 5 minutes remaining
- Use Keycloak token endpoint with `refresh_token` grant
- Update session atomically in Redis
- Handle refresh failures (redirect to login)
- Location: `webapp/src/main/kotlin/io/ktor/foodies/server/security/UserSessionScope.kt`

### Observability

**bd-3e1**: Add authentication and authorization metrics
- Instrument with OpenTelemetry metrics:
  - `auth_requests_total{service, outcome}`
  - `auth_duration_seconds{service}`
  - `token_refresh_total{service}`
  - `authorization_decisions_total{service, resource, outcome}`
  - `service_token_cache_hits_total`
- Add structured logging for auth events
- Configure Prometheus scraping endpoints

**bd-283**: Write integration tests for service-to-service authentication
- Use TestContainers to spin up Keycloak (no mocks)
- Test service token acquisition and `TokenResult` handling
- Test dual-token propagation through service chain
- Test composable DSL (`withServiceAuth`, `requireScope`, `requireResourceOwnership`)
- Test authorization result types in when expressions
- Test failure scenarios return proper result types (not exceptions):
  - Expired tokens → `AuthResult.Invalid`
  - Invalid scopes → `AuthorizationResult.Unauthorized`
  - Missing authentication → `AuthResult.Unauthenticated`
  - Token tampering → `JwtVerifyResult.Invalid`
- Verify no exceptions used for control flow
- Location: `server-shared/src/test/kotlin/io/ktor/foodies/server/openid/`

## Phase 3: Production Hardening (P3 - Post-Migration)

### Documentation

**bd-3f9**: Document service-to-service authentication patterns
- Create developer guide with code examples
- Document `ServiceTokenProvider` usage and `TokenResult` handling
- Document `AuthContext` sealed interface (UserAuth vs ServiceAuth)
- Document composable DSL with context receivers:
  - `withServiceAuth` usage
  - `requireScope()` with `AuthorizationResult`
  - `requireResourceOwnership()` patterns
- Explain result type patterns (no exceptions for control flow)
- Include migration guide from legacy pattern
- Add troubleshooting section for common auth issues
- Location: `docs/authentication.md`

### Security Hardening

**bd-1ym**: Configure TLS/mTLS for production
- Install cert-manager in Kubernetes cluster
- Configure TLS certificates for ingress
- Optionally set up service mesh (Istio/Linkerd) for mTLS
- Update service configs to enable TLS
- Test certificate rotation

**bd-26p**: Implement rate limiting per service identity
- Add `RateLimit` plugin to all services
- Key rate limits by service identity from `ServiceAuthContext`
- Configure limits (e.g., 1000 requests/min per service)
- Return `429 Too Many Requests` when exceeded
- Log rate limit violations

**bd-1ka**: Set up monitoring alerts for authentication failures
- Configure Prometheus alerting rules:
  - Auth failure rate > 1% for 5 minutes
  - Service token acquisition failures
  - JWKS fetch failures
  - Unusual authorization denial patterns
- Set up AlertManager routing to on-call
- Test alert firing and recovery

## Implementation Dependencies

```
Phase 1 (Foundation):
  bd-3r2 (Keycloak config)
    └── bd-3vg (K8s secrets)
          └── bd-3nx (Result types) ← NEW: Must be first code task
                └── bd-1od (ServiceTokenProvider)
                      └── bd-1ln (AuthContext)
                            ├── bd-2qa (ServiceAuthContext)
                            └── bd-rzz (Composable DSL)

Phase 2 (Migration):
  Phase 1 complete
    └── bd-1w3 (WebApp) ← Must be first service
          ├── bd-1sq (Basket)
          ├── bd-39x (Menu)
          ├── bd-3pl (Order)
          ├── bd-3u0 (Profile)
          └── bd-13w (Payment)

  Independent (can run in parallel):
    ├── bd-vdu (PKCE)
    ├── bd-3vs (Token refresh)
    ├── bd-3e1 (Metrics)
    └── bd-283 (Integration tests)

Phase 3 (Production):
  Phase 2 complete
    ├── bd-3f9 (Documentation)
    ├── bd-1ym (TLS/mTLS)
    ├── bd-26p (Rate limiting)
    └── bd-1ka (Alerts)
```

## Migration Strategy

### Week 1: Foundation
1. Complete Phase 1 tasks (bd-3r2, bd-3vg, bd-1od, bd-1ln, bd-2qa, bd-rzz)
2. Deploy updated `server-shared` library to all services
3. Verify services start successfully with new library

### Week 2: WebApp + Menu/Basket
1. Migrate WebApp (bd-1w3)
2. Deploy with `enforceServiceAuth: false` (backward compatible)
3. Migrate Menu service (bd-39x)
4. Migrate Basket service (bd-1sq)
5. Verify WebApp → Menu and WebApp → Basket flows work

### Week 3: Order/Profile/Payment
1. Migrate Order service (bd-3pl)
2. Migrate Profile service (bd-3u0)
3. Migrate Payment service (bd-13w)
4. Verify end-to-end order flow

### Week 4: Validation & Enforcement
1. Monitor authentication metrics
2. Verify no legacy pattern usage in logs
3. Enable enforcement: `enforceServiceAuth: true`
4. Remove backward compatibility code
5. Complete Phase 2 tasks (PKCE, token refresh, metrics, tests)

### Week 5+: Production Hardening
1. Complete Phase 3 tasks
2. Enable TLS/mTLS
3. Configure rate limiting
4. Set up alerts
5. Finalize documentation

## Testing Checklist

- [ ] User can log in via OAuth2 with PKCE
- [ ] Session stored in Redis with correct TTL
- [ ] Access tokens automatically refresh before expiration
- [ ] WebApp can call Basket service with service token
- [ ] WebApp can call Menu service with service token
- [ ] Order service can call Basket service with service token
- [ ] User context propagates correctly through service chain
- [ ] Scope validation blocks unauthorized service calls
- [ ] Resource ownership checks prevent cross-user access
- [ ] Admin role overrides ownership checks
- [ ] Expired tokens rejected with 401
- [ ] Missing tokens rejected with 401
- [ ] Invalid scopes rejected with 403
- [ ] Rate limiting enforced correctly
- [ ] Metrics collected for all auth events
- [ ] Alerts fire on authentication failures

## Rollback Plan

### Rollback Triggers
- Authentication failure rate > 1%
- Service-to-service call failure rate > 5%
- Unable to obtain service tokens from Keycloak

### Rollback Steps
1. Set `enforceServiceAuth: false` in all services
2. Redeploy previous version of services
3. Verify legacy pattern works
4. Investigate root cause
5. Fix issue and re-test before retry

## Configuration Reference

### Environment Variables

All services will support:
```yaml
# Service Identity
SERVICE_CLIENT_ID: service-{name}
SERVICE_CLIENT_SECRET: <from-k8s-secret>
TOKEN_ENDPOINT: http://keycloak:8000/realms/foodies-keycloak/protocol/openid-connect/token

# Authorization
ENFORCE_SERVICE_AUTH: true  # Set to false during migration
ENFORCE_USER_CONTEXT: false  # Require user context for all requests

# Existing
AUTH_ISSUER: http://keycloak:8000/realms/foodies-keycloak
AUTH_AUDIENCE: foodies
```

### Service Scopes

```yaml
WebApp:
  - basket:items:read
  - basket:items:write
  - menu:items:read
  - order:orders:read
  - order:orders:write

Order:
  - basket:items:read

Basket:
  - menu:items:read

Menu: []  # No downstream calls

Profile: []  # Event-driven

Payment: []  # Event-driven
```

## Success Metrics

### Security
- 100% of service-to-service calls authenticated
- 0 authorization bypass incidents
- < 1% authentication failure rate in steady state

### Performance
- Token validation latency < 10ms (p95)
- Token acquisition (cached) < 1ms
- Token acquisition (fresh) < 100ms
- Session lookup < 5ms (p95)

### Reliability
- 99.9% auth service availability
- < 0.1% token refresh failure rate
- Graceful degradation on Keycloak downtime

## References

- **Specification**: `AUTHENTICATION_SPEC.md`
- **Current Implementation**: Explored by agent a4d8ad0
- **Issue Tracker**: `br show bd-2g4`
- **Code Locations**: See individual task descriptions
- **Code Style**: No exceptions for control flow, use sealed interfaces for result types
- **Existing Patterns**: See `PaymentResult`, `GetOrderResult`, `UserSessionScope` for examples


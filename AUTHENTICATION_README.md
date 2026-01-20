# Authentication Implementation

This directory contains the comprehensive specification and implementation plan for authentication in the Foodies microservices platform.

## Quick Start

### View the Specification
```bash
cat AUTHENTICATION_SPEC.md
```

### View the Implementation Plan
```bash
cat IMPLEMENTATION_PLAN.md
```

### Track Implementation Progress
```bash
# View the main epic
br show bd-2g4

# List all authentication tasks
br list | grep -E "bd-2g4|bd-3r2|bd-1od|bd-1ln|bd-2qa|bd-rzz|bd-3vg|bd-1w3|bd-1sq|bd-39x|bd-3pl|bd-3u0|bd-13w|bd-vdu|bd-3vs|bd-3e1|bd-283|bd-3f9|bd-1ym|bd-26p|bd-1ka"

# View ready tasks
br ready
```

## Documents

### 1. AUTHENTICATION_SPEC.md
**Comprehensive technical specification** covering:
- Current state analysis
- Architecture principles (Zero Trust, Defense in Depth)
- User authentication flows (OAuth2 + PKCE)
- Service-to-service authentication (dual-token pattern)
- Authorization model (RBAC, ABAC, scopes)
- Security mechanisms
- Operational considerations
- Migration strategy

### 2. IMPLEMENTATION_PLAN.md
**Task-oriented implementation guide** with:
- Phased rollout plan (3 phases)
- Task dependencies and sequencing
- Week-by-week migration timeline
- Testing checklist
- Rollback procedures
- Configuration reference
- Success metrics

## Architecture Overview

### Current State
- **User Auth**: OAuth2 with Keycloak, session-based (Redis)
- **Service Auth**: User JWT token propagation (no service identity)
- **Gap**: No service-to-service authentication mechanism

### Target State
- **User Auth**: OAuth2 with PKCE, proactive token refresh
- **Service Auth**: Dual-token pattern
  - Service tokens (OAuth2 client credentials)
  - Optional user context propagation
- **Authorization**: Scope-based + RBAC + ABAC

## Key Concepts

### Dual-Token Pattern
```kotlin
// Service acquires token and makes authenticated call
when (val tokenResult = serviceTokenProvider.getToken("basket")) {
    is TokenResult.Success -> {
        val authContext = AuthContext.ServiceAuth(
            serviceToken = tokenResult.token,
            userToken = userSession.accessToken
        )

        withContext(authContext) {
            httpClient.post("/basket/items") { ... }
        }

        // HTTP Headers sent:
        // Authorization: Bearer {service_token}
        // X-User-Context: {user_token}
    }
    is TokenResult.Failed -> {
        // Handle token acquisition failure
        logger.error("Failed to acquire token: ${tokenResult.reason}")
    }
}
```

### Scope-Based Authorization
```kotlin
// Using composable DSL with context receivers
context(ServiceAuthScope)
suspend fun Route.basketRoutes() {
    route("/basket/items") {
        post {
            when (requireScope("basket:items:write")) {
                is AuthorizationResult.Authorized -> {
                    // Only services with basket:items:write scope reach here
                    call.respond(HttpStatusCode.OK)
                }
                is AuthorizationResult.Unauthorized -> {
                    call.respond(HttpStatusCode.Forbidden, "Missing scope")
                }
                is AuthorizationResult.Forbidden -> {
                    call.respond(HttpStatusCode.Forbidden)
                }
            }
        }
    }
}
```

### Resource Ownership
```kotlin
// Users can only access their own basket - no exceptions!
context(ServiceAuthScope)
suspend fun Route.basketRoutes() {
    get("/basket") {
        val authContext = serviceAuthContext()
        val basket = basketRepository.findByBuyerId(userId)

        when (requireResourceOwnership(basket.buyerId)) {
            is AuthorizationResult.Authorized -> {
                call.respond(basket)
            }
            is AuthorizationResult.Forbidden -> {
                call.respond(HttpStatusCode.Forbidden, "Not resource owner")
            }
            is AuthorizationResult.Unauthorized -> {
                call.respond(HttpStatusCode.Unauthorized)
            }
        }
    }
}
```

## Implementation Phases

### Phase 1: Foundation (Week 1)
**Priority**: P1 (Critical)
- Register service principals in Keycloak
- Create Kubernetes secrets
- Implement result type definitions (sealed interfaces)
- Implement core auth library components with composable DSL
- Deploy updated server-shared module

**Key Tasks**: bd-3r2, bd-3vg, bd-3nx, bd-1od, bd-1ln, bd-2qa, bd-rzz

### Phase 2: Service Migration (Weeks 2-4)
**Priority**: P2 (High)
- Migrate services one by one
- Start with WebApp (gateway)
- Then Menu, Basket, Order, Profile, Payment
- Add user auth enhancements (PKCE, token refresh)
- Implement observability

**Key Tasks**: bd-1w3, bd-1sq, bd-39x, bd-3pl, bd-3u0, bd-13w, bd-vdu, bd-3vs, bd-3e1, bd-283

### Phase 3: Production Hardening (Week 5+)
**Priority**: P3 (Medium)
- Complete documentation
- Configure TLS/mTLS
- Add rate limiting
- Set up alerts

**Key Tasks**: bd-3f9, bd-1ym, bd-26p, bd-1ka

## Quick Commands

### Working with br (issue tracker)
```bash
# Show epic details
br show bd-2g4

# Show specific task
br show bd-1od

# List ready tasks (unblocked)
br ready

# Update task status
br update bd-1od --status in-progress

# Close completed task
br close bd-1od

# Add comment to task
br comments add bd-1od "Implemented ServiceTokenProvider with caching"

# View project statistics
br stats
```

### Development Workflow
```bash
# Start working on a task
br update <task-id> --status in-progress

# Build and test
./gradlew :server-shared:build
./gradlew :server-shared:jvmTest

# Close task when done
br close <task-id>
```

## Service Scope Matrix

| Service | Scopes Required |
|---------|----------------|
| WebApp | basket:items:read, basket:items:write, menu:items:read, order:orders:read, order:orders:write |
| Order | basket:items:read |
| Basket | menu:items:read |
| Menu | (none - no downstream calls) |
| Profile | (none - event-driven) |
| Payment | (none - event-driven) |

## Testing Strategy

### Unit Tests
- Token validation logic
- Scope parsing and authorization
- Token caching and refresh

### Integration Tests
- Full authentication flow with Keycloak (TestContainers)
- Service-to-service call chains
- Authorization enforcement
- Failure scenarios

### Security Tests
- Token tampering detection
- Replay attack prevention
- Scope escalation attempts
- Missing authentication handling

## Migration Checklist

- [ ] Phase 1: Foundation complete
  - [ ] Services registered in Keycloak
  - [ ] Secrets created in Kubernetes
  - [ ] server-shared library updated
  - [ ] All services deployed with new library
- [ ] Phase 2: Migration complete
  - [ ] WebApp migrated
  - [ ] All backend services migrated
  - [ ] PKCE implemented
  - [ ] Token refresh implemented
  - [ ] Metrics and tests complete
- [ ] Phase 3: Hardening complete
  - [ ] Documentation finalized
  - [ ] TLS/mTLS configured
  - [ ] Rate limiting enabled
  - [ ] Alerts configured

## Rollback Strategy

If authentication failures exceed 1%:
1. Set `ENFORCE_SERVICE_AUTH=false` in all deployments
2. Redeploy previous service versions
3. Investigate issues
4. Fix and re-test before retry

## Success Criteria

### Security
- ✅ 100% service-to-service calls authenticated
- ✅ Zero authorization bypass incidents
- ✅ < 1% authentication failure rate

### Performance
- ✅ Token validation < 10ms (p95)
- ✅ Session lookup < 5ms (p95)
- ✅ Token acquisition (cached) < 1ms

### Reliability
- ✅ 99.9% auth service availability
- ✅ Graceful degradation on failures

## Key Files

```
auth/
├── AUTHENTICATION_README.md         # This file
├── AUTHENTICATION_SPEC.md           # Technical specification
├── IMPLEMENTATION_PLAN.md           # Implementation guide
└── (implementation to be created in workspace)

server-shared/src/main/kotlin/io/ktor/foodies/server/openid/
├── AuthContext.kt                   # Existing, enhanced to sealed interface
├── OpenIdConfiguration.kt           # Existing
├── Security.kt                      # Existing
├── AuthResult.kt                    # New - Result types
├── AuthorizationResult.kt           # New - Authorization result types
├── TokenResult.kt                   # New - Token acquisition result types
├── JwtVerifyResult.kt               # New - JWT validation result types
├── ServiceTokenProvider.kt          # New - Token acquisition
├── ServiceAuthContext.kt            # New - Auth context data
├── ServiceAuthScope.kt              # New - Composable DSL scope
└── Authorization.kt                 # New - Authorization helpers

webapp/src/main/kotlin/io/ktor/foodies/server/security/
├── UserSessionScope.kt              # Existing, to be enhanced
└── Security.kt                      # Existing, to be enhanced
```

## Support

For questions or issues:
1. Check `AUTHENTICATION_SPEC.md` for architectural decisions
2. Check `IMPLEMENTATION_PLAN.md` for implementation guidance
3. Use `br` to track and discuss specific tasks
4. Review existing code in server-shared/openid/ for patterns

## References

- **OAuth 2.0**: RFC 6749
- **PKCE**: RFC 7636
- **JWT**: RFC 7519
- **OpenID Connect**: https://openid.net/specs/openid-connect-core-1_0.html
- **Zero Trust Architecture**: NIST SP 800-207


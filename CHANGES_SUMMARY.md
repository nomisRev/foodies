# Summary of Changes to Authentication Specification and Implementation Plan

**Date**: 2026-01-20
**Purpose**: Improve authentication design to use composable typesafe DSL and avoid exceptions for control flow

## Key Improvements

### 1. **Eliminated Exception-Based Control Flow**

**Before:**
```kotlin
fun requireScope(scope: String) {
    if (!authContext.scopes.contains(scope)) {
        throw ForbiddenException("Missing required scope")
    }
}
```

**After:**
```kotlin
sealed interface AuthorizationResult {
    data object Authorized : AuthorizationResult
    data class Unauthorized(val missingScope: String) : AuthorizationResult
    data class Forbidden(val reason: String) : AuthorizationResult
}

context(ServiceAuthContext)
suspend fun requireScope(scope: String): AuthorizationResult {
    return if (scopes.contains(scope)) {
        AuthorizationResult.Authorized
    } else {
        AuthorizationResult.Unauthorized(scope)
    }
}
```

### 2. **Introduced Result Types**

Added sealed interfaces for all authentication/authorization operations:

- **`AuthResult<T>`**: Authentication results (Authenticated, Unauthenticated, Invalid)
- **`AuthorizationResult`**: Authorization decisions (Authorized, Unauthorized, Forbidden)
- **`TokenResult`**: Token acquisition results (Success, Failed)
- **`JwtVerifyResult`**: JWT validation results (Valid, Invalid, Expired)

### 3. **Created Composable DSL with Context Receivers**

**Before:**
```kotlin
val ServiceAuthorizationPlugin = createApplicationPlugin("ServiceAuthorization") {
    onCall { call ->
        val authContext = validateServiceRequest(call)
        call.attributes.put(ServiceAuthContextKey, authContext)
    }
}
```

**After:**
```kotlin
fun interface ServiceAuthScope {
    context(ctx: RoutingContext)
    suspend fun serviceAuthContext(): ServiceAuthContext
}

fun Route.withServiceAuth(
    build: context(ServiceAuthScope) Route.() -> Unit
): Route {
    // Validates auth and provides context
}

// Usage
context(ServiceAuthScope)
suspend fun Route.basketRoutes() {
    route("/basket/items") {
        post {
            when (requireScope("basket:items:write")) {
                AuthorizationResult.Authorized -> { /* handle */ }
                is AuthorizationResult.Unauthorized -> { /* handle */ }
                is AuthorizationResult.Forbidden -> { /* handle */ }
            }
        }
    }
}
```

### 4. **Enhanced AuthContext to Sealed Interface**

**Before:**
```kotlin
data class AuthContext(val accessToken: String)
```

**After:**
```kotlin
sealed interface AuthContext : AbstractCoroutineContextElement {
    companion object Key : CoroutineContext.Key<AuthContext>

    data class UserAuth(val accessToken: String) : AuthContext
    data class ServiceAuth(
        val serviceToken: String,
        val userToken: String? = null
    ) : AuthContext
}
```

### 5. **Updated ServiceTokenProvider to Return Result Types**

**Before:**
```kotlin
suspend fun getToken(targetService: String): String {
    // Throws exceptions on failure
}
```

**After:**
```kotlin
suspend fun getToken(targetService: String): TokenResult {
    return try {
        // Token acquisition logic
        TokenResult.Success(token, expiresAt)
    } catch (e: Exception) {
        TokenResult.Failed("Failed to acquire service token", e)
    }
}
```

## Changes to AUTHENTICATION_SPEC.md

### Sections Modified:

1. **Section 4.5 - User Context Propagation**
   - Changed `AuthContext` to sealed interface
   - Updated `AuthContextPlugin` to handle both UserAuth and ServiceAuth

2. **Section 4.6 - Token Validation**
   - Added result type definitions
   - Changed `validateServiceRequest()` to return `AuthResult<ServiceAuthContext>`
   - Removed exception throwing

3. **Section 4.7 - Service Token Caching**
   - Added `TokenResult` type
   - Updated `ServiceTokenProvider.getToken()` to return `TokenResult`

4. **Section 5.2 - Scope-Based Authorization**
   - Added `AuthorizationResult` type
   - Changed `requireScope()` to return result instead of throwing
   - Added composable DSL usage examples

5. **Section 5.3 - ABAC**
   - Changed `checkResourceOwnership()` to `requireResourceOwnership()`
   - Returns `AuthorizationResult` instead of throwing exceptions
   - Added usage example with when expressions

6. **Section 5.4 - Authorization Plugin**
   - Replaced plugin with composable DSL using context receivers
   - Added `ServiceAuthScope` and `withServiceAuth()`
   - Pattern matches existing `UserSessionScope`

7. **Section 5.5 - NEW: Composable DSL Patterns**
   - Added new section documenting design philosophy
   - Complete usage examples
   - Benefits of the approach

8. **Section 7.2 - Shared Library Components**
   - Added new files for result types
   - Added `ServiceAuthScope.kt` and `Authorization.kt`

## Changes to IMPLEMENTATION_PLAN.md

### New Task Added:

**bd-3nx**: Create result type definitions
- Must be completed first in Phase 1
- Defines all sealed interfaces for result types

### Modified Tasks:

1. **bd-1od**: ServiceTokenProvider
   - Now returns `TokenResult` instead of throwing exceptions

2. **bd-1ln**: AuthContext enhancement
   - Convert to sealed interface with UserAuth/ServiceAuth variants
   - Update plugin to handle both types

3. **bd-2qa**: ServiceAuthContext
   - Return `AuthResult<ServiceAuthContext>` from validation
   - No exceptions for control flow

4. **bd-rzz**: Authorization DSL
   - Create composable DSL with context receivers
   - Return result types from authorization helpers
   - Pattern similar to `UserSessionScope`

5. **bd-1w3**: WebApp migration
   - Handle `TokenResult` properly
   - Use `AuthContext.ServiceAuth` and `AuthContext.UserAuth`

6. **bd-1sq, bd-39x, bd-3pl, bd-3u0, bd-13w**: Backend services
   - Use `withServiceAuth` DSL
   - Handle `AuthorizationResult` in when expressions
   - Consistent with existing result type patterns

7. **bd-283**: Integration tests
   - Test result types instead of exception handling
   - Verify no exceptions for control flow

8. **bd-3f9**: Documentation
   - Document composable DSL patterns
   - Document result type handling

### Updated Dependencies:

Added bd-3nx as first task in dependency chain:
```
bd-3r2 → bd-3vg → bd-3nx → bd-1od → bd-1ln → {bd-2qa, bd-rzz}
```

## Changes to AUTHENTICATION_README.md

### Modified Sections:

1. **Dual-Token Pattern example**
   - Shows `TokenResult` handling
   - Uses `AuthContext.ServiceAuth`

2. **Scope-Based Authorization example**
   - Shows composable DSL with context receivers
   - Uses when expressions for `AuthorizationResult`

3. **Resource Ownership example**
   - Shows `requireResourceOwnership()` with result types
   - No exceptions

4. **Key Files section**
   - Added new result type files
   - Updated descriptions

5. **Phase 1 tasks**
   - Added bd-3nx to key tasks

## Design Philosophy

### Core Principles:

1. **No exceptions for control flow** - Follow Kotlin and project best practices
2. **Type-safe** - Compile-time verification of authorization requirements
3. **Composable** - Mix and match authorization requirements using context receivers
4. **Exhaustive** - Compiler ensures all result cases are handled
5. **Testable** - Easy to unit test authorization logic
6. **Consistent** - Follows existing patterns in the codebase (e.g., `PaymentResult`, `GetOrderResult`, `UserSessionScope`)

### Benefits:

✅ **Code clarity**: When expressions make all paths explicit
✅ **Type safety**: Sealed interfaces prevent invalid states
✅ **Composability**: Context receivers enable elegant DSL
✅ **Maintainability**: Consistent patterns across codebase
✅ **Performance**: No exception overhead for normal control flow
✅ **Testing**: Result types are easy to mock and test

## Migration Path

The changes maintain backward compatibility during migration:
- Services can adopt new patterns incrementally
- Legacy exception-based code can coexist temporarily
- Clear migration path defined in implementation plan

## Next Steps

1. Review and approve changes
2. Create tasks in `br` issue tracker for bd-3nx
3. Begin Phase 1 implementation with result types
4. Follow updated implementation plan

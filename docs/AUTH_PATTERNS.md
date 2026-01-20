# Authentication & Authorization Patterns

This document describes the recommended patterns for authentication and authorization in the Foodies workspace, following the migration to result-based control flow and composable DSLs.

## 1. Service-to-Service Authentication

To call another service, use `ServiceTokenProvider` to obtain a service token.

### Obtaining a Service Token

```kotlin
val tokenResult = serviceTokenProvider.getToken("basket-service")
when (tokenResult) {
    is TokenResult.Success -> {
        // Use result.token
    }
    is TokenResult.Failed -> {
        // Handle failure (e.g., log and return error)
    }
}
```

### Automatic Token Injection in Ktor Client

Ideally, configure the `HttpClient` to automatically inject the service token:

```kotlin
val httpClient = HttpClient(CIO) {
    install(Auth) {
        bearer {
            loadTokens {
                when (val result = serviceTokenProvider.getToken(targetService)) {
                    is TokenResult.Success -> BearerTokens(result.token, "")
                    else -> null
                }
            }
        }
    }
}
```

## 2. Handling Authentication Results

Instead of throwing exceptions, authentication functions return `AuthResult<T>`.

```kotlin
val authResult = validateServiceRequest(servicePrincipal, userPrincipal)
when (authResult) {
    is AuthResult.Authenticated -> {
        val context = authResult.context
        // Proceed with authenticated context
    }
    is AuthResult.Unauthenticated -> {
        call.respond(HttpStatusCode.Unauthorized)
    }
    is AuthResult.Invalid -> {
        call.respond(HttpStatusCode.Forbidden, authResult.reason)
    }
}
```

## 3. Authorization with Composable DSL

Use the `withServiceAuth` DSL to handle service-level authentication and provide a `ServiceAuthContext`.

### Scope-Based Authorization

```kotlin
route("/items") {
    withServiceAuth {
        post {
            when (requireScope("basket:write")) {
                AuthorizationResult.Authorized -> {
                    // Logic for authorized request
                }
                is AuthorizationResult.Unauthorized -> {
                    call.respond(HttpStatusCode.Forbidden, "Missing scope: ${it.missingScope}")
                }
                is AuthorizationResult.Forbidden -> {
                    call.respond(HttpStatusCode.Forbidden, it.reason)
                }
            }
        }
    }
}
```

### Resource Ownership (ABAC)

```kotlin
when (requireResourceOwnership(resourceId, ownerId)) {
    AuthorizationResult.Authorized -> { /* ... */ }
    is AuthorizationResult.Forbidden -> { /* ... */ }
    else -> { /* ... */ }
}
```

## 4. Migration from Legacy Patterns

### Legacy (Exceptions)
```kotlin
// DON'T DO THIS
fun checkAuth() {
    if (!authenticated) throw UnauthorizedException()
}
```

### New (Result Types)
```kotlin
// DO THIS
fun checkAuth(): AuthResult<User> {
    return if (authenticated) AuthResult.Authenticated(user)
    else AuthResult.Unauthenticated
}
```

## 5. Troubleshooting

- **401 Unauthorized**: Check if the `Authorization` header is correctly set and the token is not expired.
- **403 Forbidden**: Check if the service token has the required scopes (`scp` claim) or if the user has ownership of the resource.
- **Token Acquisition Failure**: Check `ServiceTokenProvider` logs for connection issues to Keycloak or invalid client credentials.

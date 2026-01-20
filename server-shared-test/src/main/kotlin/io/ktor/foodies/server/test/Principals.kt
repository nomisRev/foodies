package io.ktor.foodies.server.test

import io.ktor.foodies.server.openid.AUTH_SERVICE
import io.ktor.foodies.server.openid.AUTH_USER
import io.ktor.foodies.server.openid.AuthPrincipal
import io.ktor.foodies.server.openid.ServicePrincipal
import io.ktor.foodies.server.openid.UserPrincipal
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationProvider

fun testUserPrincipal(
    userId: String = "test-user",
    email: String? = "test@example.com",
    name: String? = "Test User",
    roles: Set<String> = setOf("user"),
    scopes: Set<String> = setOf("openid", "profile", "email")
) = UserPrincipal(userId, email, name, roles, scopes)

fun testServicePrincipal(
    serviceId: String = "test-service",
    scopes: Set<String> = setOf("service")
) = ServicePrincipal(serviceId, scopes)

/**
 * Mocks authentication by registering a custom provider that always returns the provided principal.
 */
fun AuthenticationConfig.mockAuth(principal: AuthPrincipal) {
    val name = when (principal) {
        is UserPrincipal -> AUTH_USER
        is ServicePrincipal -> AUTH_SERVICE
    }
    register(object : AuthenticationProvider(object : AuthenticationProvider.Config(name) {}) {
        override suspend fun onAuthenticate(context: AuthenticationContext) {
            context.principal(principal)
        }
    })
}

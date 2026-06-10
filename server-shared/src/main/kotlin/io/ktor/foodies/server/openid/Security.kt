package io.ktor.foodies.server.openid

import io.ktor.foodies.server.auth.UserPrincipal
import io.ktor.server.application.*
import io.ktor.server.auth.oidc.*
import io.ktor.server.auth.typesafe.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable

@Serializable
data class Auth(
    val issuer: String,
    val audience: String,
)

@OptIn(ExperimentalKtorApi::class)
enum class UserRole : AuthRole {
    ADMIN,
    USER;

    companion object {
        fun fromClaim(claim: String): UserRole? {
            return entries.find { it.name.equals(other = claim, ignoreCase = true) }
        }
    }
}

@OptIn(ExperimentalKtorApi::class)
suspend fun Application.security(auth: Auth): RoleBasedAuthScheme<UserPrincipal, UserRole> {
    val oidc = openIdConnect {}
    val keycloak = oidc.provider(
        name = "keycloak",
        transformPrincipal = transform@{
            val accessToken = it as? OidcToken.Access ?: return@transform null
            if (accessToken.userInfo?.email == null) return@transform null
            UserPrincipal(accessToken)
        }
    ) {
        issuer = auth.issuer
        accessToken { audiences = setOf(auth.audience) }
        bearer()
    }
    val authScheme = keycloak.bearer.withRoles { it.realmRoles() }
    attributes.put(AuthSchemeKey, authScheme)
    return authScheme
}

private fun UserPrincipal.realmRoles(): Set<UserRole> {
    return roles.mapNotNull { UserRole.fromClaim(it) }.toSet()
}

@OptIn(ExperimentalKtorApi::class)
val AuthSchemeKey = AttributeKey<RoleBasedAuthScheme<UserPrincipal, UserRole>>("AuthSchemeKey")


@OptIn(ExperimentalKtorApi::class)
val Application.authScheme: RoleBasedAuthScheme<UserPrincipal, UserRole>
    get() = attributes[AuthSchemeKey]

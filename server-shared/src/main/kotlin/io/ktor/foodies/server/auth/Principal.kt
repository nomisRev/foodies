package io.ktor.foodies.server.auth

import io.ktor.server.auth.oidc.OidcToken
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class UserPrincipal(
    val userId: String,
    val email: String?,
    val roles: Set<String>,
    val name: String? = null,
    val accessToken: OidcToken.Access,
) {
    constructor(principal: OidcToken.Access) : this(
        userId = requireNotNull(principal.userInfo?.subject) { "subject claim is missing" },
        email = principal.userInfo?.email,
        roles = principal.realmRoles(),
        name = principal.userInfo?.name ?: principal.userInfo?.preferredUsername,
        accessToken = principal,
    )
}

data class ServicePrincipal(
    val serviceAccountId: String,
    val clientId: String,
    val roles: Set<String>,
    val userContext: UserPrincipal? = null
)

private fun OidcToken.Access.realmRoles(): Set<String> {
    val accessJson = claims.claim("realm_access") ?: return emptySet()
    val roles = accessJson.jsonObject["roles"]?.jsonArray ?: return emptySet()
    return roles.map { it.jsonPrimitive.content }.toSet()
}

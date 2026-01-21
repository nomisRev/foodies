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

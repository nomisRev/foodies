package io.ktor.foodies.server.auth

data class UserPrincipal(
    val userId: String,
    val email: String?,
    val roles: Set<String>,
    val accessToken: String
)

data class ServicePrincipal(
    val serviceAccountId: String,
    val clientId: String,
    val roles: Set<String>,
    val userContext: UserPrincipal? = null
)

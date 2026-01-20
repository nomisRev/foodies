package io.ktor.foodies.server.openid

import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.util.AttributeKey

data class ServiceAuthContext(
    val serviceId: String,
    val servicePrincipal: JWTPrincipal,
    val scopes: List<String>,
    val userPrincipal: JWTPrincipal? = null
)

val ServiceAuthContextKey = AttributeKey<ServiceAuthContext>("ServiceAuthContext")

fun validateServiceRequest(
    servicePrincipal: JWTPrincipal,
    userPrincipal: JWTPrincipal? = null
): AuthResult<ServiceAuthContext> {
    val payload = servicePrincipal.payload
    
    val serviceId = payload.subject ?: return AuthResult.Invalid("Missing service identity in token")
    
    val scopes = try {
        payload.getClaim("scp").asList(String::class.java) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
    
    return AuthResult.Authenticated(
        ServiceAuthContext(
            serviceId = serviceId,
            servicePrincipal = servicePrincipal,
            scopes = scopes,
            userPrincipal = userPrincipal
        )
    )
}

fun ServiceAuthContext.requireScope(scope: String): AuthorizationResult {
    return if (scopes.contains(scope)) {
        AuthorizationResult.Authorized
    } else {
        AuthorizationResult.Unauthorized(scope)
    }
}

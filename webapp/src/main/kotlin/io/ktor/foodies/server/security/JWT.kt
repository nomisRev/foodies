package io.ktor.foodies.server.security

import com.auth0.jwk.JwkProvider
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

internal fun AuthenticationConfig.jwt(jwks: JwkProvider, issuer: String, leeway: Long = 3) = jwt {
    verifier(jwks, issuer) {
        acceptLeeway(leeway)
        withAudience("foodies")
    }
    authHeader { call ->
        call.sessions.get<UserSession>()?.idToken?.let { HttpAuthHeader.Single("Bearer", it) }
    }
    validate { _ -> sessions.get<UserSession>() }
    challenge { _, _ ->
        call.response.headers.append("HX-Redirect", "/login")
        call.respond(Unauthorized)
    }
}

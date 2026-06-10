package io.ktor.foodies.webapp.security

import io.ktor.client.*
import io.ktor.foodies.webapp.*
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.server.application.*
import io.ktor.server.auth.oidc.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import java.util.*
import kotlin.time.Duration.Companion.seconds

suspend fun Application.security(
    config: Config.Security,
    httpClient: HttpClient,
    sessionStorage: SessionStorage
): OidcProvider<OidcToken.Id> {
    val oidc = openIdConnect {
        this.httpClient = httpClient
    }

    val keycloak: OidcProvider<OidcToken.Id> = oidc.provider(
        name = "keycloak",
        transformPrincipal = { it as? OidcToken.Id }
    ) {
        issuer = config.issuer
        accessToken {
            audiences = setOf(config.audience)
        }
        bearer()
        oauth {
            clientId = config.clientId
            clientSecret = config.clientSecret

            config.stateEncryptionKey?.takeIf { it.isNotBlank() }?.let {
                val key = Base64.getDecoder().decode(it)
                stateEncryptionKey = OidcStateEncryptionKey.of(key)
            }

            scopes = listOf("openid", "profile", "email", "offline_access")
            loginUri = { path("login") }
            redirectUri = { path("oauth", "callback") }
            postLogoutRedirectUri = { path("/") }

            onSuccess { call.respondRedirect("/") }

            onFailure {
                call.response.headers.append("HX-Redirect", "/login")
                call.respond(Unauthorized)
            }
        }
        sessions {
            storage = sessionStorage
            logoutUri = { path("logout") }
            tokenRefreshStrategy = OidcTokenRefreshStrategy.Auto(beforeExpiry = 60.seconds)
            cookie {
                cookie.secure = false
            }
        }
    }

    return keycloak
}

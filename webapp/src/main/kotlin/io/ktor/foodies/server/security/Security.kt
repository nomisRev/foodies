package io.ktor.foodies.server.security

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.client.HttpClient
import io.ktor.foodies.server.Config
import io.ktor.foodies.server.openid.OpenIdConfiguration
import io.ktor.foodies.server.openid.discover
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.URLBuilder
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.oauth
import io.ktor.server.plugins.origin
import io.ktor.server.request.host
import io.ktor.server.request.port
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionStorage
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.html.Entities
import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
data class UserSession(
    val idToken: String,
    val accessToken: String,
    val expiresIn: Long,
    val refreshToken: String? = null
)

@OptIn(ExperimentalLettuceCoroutinesApi::class)
suspend fun Application.security(
    config: Config.Security,
    httpClient: HttpClient,
    sessionStorage: SessionStorage
) {
    install(Sessions) {
        cookie<UserSession>("USER_SESSION", sessionStorage) {
            // Set secure=false for local development with HTTP
            // In production with HTTPS, this should be true and use ForwardedHeaders plugin
            cookie.secure = false
            cookie.httpOnly = true
            cookie.extensions["SameSite"] = "Lax"
        }
    }

    val openIdConfig = httpClient.discover(config.issuer)
    log.info("Loading $openIdConfig")

    authentication {
        oauth(openIdConfig, config, httpClient)
        jwt(JwkProviderBuilder(URI(openIdConfig.jwksUri).toURL()).build(), openIdConfig.issuer)
    }

    routing {
        authenticate("oauth") {
            get("/login") { }

            get("/oauth/callback") {
                val oauth2 =
                    call.authentication.principal<OAuthAccessTokenResponse.OAuth2>()
                val idToken = oauth2?.extraParameters["id_token"]
                if (idToken == null) {
                    call.respond(Unauthorized)
                } else {
                    call.sessions.set(UserSession(idToken, oauth2.accessToken, oauth2.expiresIn, oauth2.refreshToken))
                    call.respondRedirect("/")
                }
            }
        }

        get("/logout") {
            val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/")
            val redirect = "${call.request.origin.scheme}://${call.request.host()}:${call.request.port()}/"
            call.sessions.clear<UserSession>()
            call.respondRedirect(URLBuilder(openIdConfig.endSessionEndpoint).apply {
                parameters.append("id_token_hint", session.idToken)
                parameters.append("post_logout_redirect_uri", redirect)
            }.buildString())
        }
    }
}

internal fun AuthenticationConfig.jwt(jwks: JwkProvider, issuer: String) = jwt {
    verifier(jwks, issuer) {
        acceptLeeway(3)
        withAudience("foodies")
    }
    authHeader { call -> call.sessions.get<UserSession>()?.idToken?.let { HttpAuthHeader.Single("Bearer", it) } }
    validate { _ -> sessions.get<UserSession>() }
    challenge { _, _ ->
        call.response.headers.append("HX-Redirect", "/login")
        call.respond(Unauthorized)
    }
}

private fun AuthenticationConfig.oauth(
    openIdConfig: OpenIdConfiguration,
    config: Config.Security,
    httpClient: HttpClient
) = oauth("oauth") {
    client = httpClient
    urlProvider = {
        val portSuffix = when {
            request.origin.scheme == "http" && request.port() == 80 -> ""
            request.origin.scheme == "https" && request.port() == 443 -> ""
            else -> ":${request.port()}"
        }
        "${request.origin.scheme}://${request.host()}$portSuffix/oauth/callback"
    }
    providerLookup = {
        OAuthServerSettings.OAuth2ServerSettings(
            name = "foodies-oauth",
            authorizeUrl = openIdConfig.authorizationEndpoint,
            accessTokenUrl = openIdConfig.tokenEndpoint,
            requestMethod = HttpMethod.Post,
            clientId = config.clientId,
            clientSecret = config.clientSecret,
            defaultScopes = listOf("openid", "profile", "email"),
        )
    }
}

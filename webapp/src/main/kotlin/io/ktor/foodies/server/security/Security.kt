package io.ktor.foodies.server.security

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.client.HttpClient
import io.ktor.foodies.server.Config
import io.ktor.foodies.server.openid.OpenIdConfiguration
import io.ktor.foodies.server.openid.discover
import io.ktor.http.HttpMethod
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
import kotlinx.serialization.Serializable

@Serializable
data class UserSession(val idToken: String)

@OptIn(ExperimentalLettuceCoroutinesApi::class)
suspend fun Application.security(
    config: Config.Security,
    httpClient: HttpClient,
    sessionStorage: SessionStorage
) {
    install(Sessions) {
        cookie<UserSession>("USER_SESSION", sessionStorage) {
            cookie.secure = !this@security.developmentMode
            cookie.httpOnly = true
            cookie.extensions["SameSite"] = "lax"
        }
    }

    val openIdConfig = httpClient.discover(config.issuer)
    log.info("Loading $openIdConfig")

    authentication {
        oauth(openIdConfig, config, httpClient)
        jwt(openIdConfig, config)
    }

    routing {
        authenticate("oauth") {
            get("/login") { }

            get("/oauth/callback") {
                val idToken =
                    call.authentication.principal<OAuthAccessTokenResponse.OAuth2>()?.extraParameters["id_token"]
                if (idToken == null) {
                    call.respond(Unauthorized)
                } else {
                    call.sessions.set(UserSession(idToken))
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

private fun AuthenticationConfig.jwt(openIdConfig: OpenIdConfiguration, config: Config.Security) {
    jwt {
        verifier(
            JwkProviderBuilder(openIdConfig.jwksUri)
                .cached(true)
                .rateLimited(true)
                .build(),
            config.issuer
        )
        authHeader { call -> call.sessions.get<UserSession>()?.idToken?.let { HttpAuthHeader.Single("Bearer", it) } }
        validate { credential -> credential.payload.extractUserInfo() }
    }
}

private fun AuthenticationConfig.oauth(
    openIdConfig: OpenIdConfiguration,
    config: Config.Security,
    httpClient: HttpClient
) {
    oauth("oauth") {
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

        client = httpClient
    }
}

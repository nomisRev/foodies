package io.ktor.foodies.server

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.URLBuilder
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.http.fullPath
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
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
import io.ktor.server.sessions.SessionStorageMemory
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlin.text.append

@Serializable
@JsonIgnoreUnknownKeys
private data class OpenIdConfiguration(
    val issuer: String,
    @SerialName("authorization_endpoint")
    val authorizationEndpoint: String,
    @SerialName("token_endpoint")
    val tokenEndpoint: String,
    @SerialName("jwks_uri")
    val jwksUri: String,
    @SerialName("end_session_endpoint")
    val endSessionEndpoint: String
)

private suspend fun HttpClient.discover(issuer: String): OpenIdConfiguration =
    get("$issuer/.well-known/openid-configuration").body()

@Serializable
data class UserSession(val idToken: String)

data class AuthSubject(val subject: String)

suspend fun Application.security(config: Config.Security) {
    install(Sessions) {
        cookie<UserSession>("USER_SESSION", SessionStorageMemory()) {
            cookie.secure = !this@security.developmentMode
            cookie.httpOnly = true
            cookie.extensions["SameSite"] = "lax"
        }
    }

    val httpClient = HttpClient(Apache5) {
        install(ContentNegotiation) { json() }
    }
    monitor.subscribe(ApplicationStopped) { httpClient.close() }

    val openIdConfig = httpClient.discover(config.issuer)

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
        authHeader { call ->
            call.request.headers[Authorization]?.let { parseAuthorizationHeader(it) }
                ?: call.sessions.get<UserSession>()?.idToken?.let { HttpAuthHeader.Single("Bearer", it) }
        }
        validate { credential ->
            val subject = requireNotNull(credential.subject) { "JwtCredential is missing subject claim" }
            AuthSubject(subject)
        }
    }
}

private fun AuthenticationConfig.oauth(
    openIdConfig: OpenIdConfiguration,
    config: Config.Security,
    httpClient: HttpClient
) {
    oauth("oauth") {
        urlProvider = { "${request.origin.scheme}://${request.host()}:${request.port()}/oauth/callback" }
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

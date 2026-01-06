package io.ktor.foodies.server

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
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
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class OpenIdConfiguration(
    val issuer: String,
    @SerialName("authorization_endpoint")
    val authorizationEndpoint: String,
    @SerialName("token_endpoint")
    val tokenEndpoint: String,
    @SerialName("jwks_uri")
    val jwksUri: String
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

    routing {
        authenticate("oauth") {
            get("/login") { }
            get("/oauth/callback") {
                val idToken =
                    call.authentication.principal<OAuthAccessTokenResponse.OAuth2>()?.extraParameters["id_token"]
                if (idToken == null) call.respond(Unauthorized)
                else {
                    call.sessions.set(UserSession(idToken))
                    call.respondRedirect("/")
                }
            }
        }
    }
}

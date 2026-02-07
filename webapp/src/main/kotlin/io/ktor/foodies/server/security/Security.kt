package io.ktor.foodies.server.security

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.foodies.server.Config
import io.ktor.foodies.server.auth.AuthContext
import io.ktor.foodies.server.openid.OpenIdConfiguration
import io.ktor.foodies.server.openid.discover
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.URLBuilder
import io.ktor.http.parameters
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.PipelineCall
import io.ktor.server.application.call
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.oauth
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.request.host
import io.ktor.server.request.port
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionStorage
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelineContext
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("UserSessionScope")

@OptIn(ExperimentalLettuceCoroutinesApi::class)
suspend fun Application.security(
    config: Config.Security,
    httpClient: HttpClient,
    sessionStorage: SessionStorage,
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

    authentication {
        oauth(openIdConfig, config, httpClient)
        jwt(openIdConfig.jwks(), openIdConfig.issuer)
    }

    routing {
        authenticate("oauth") {
            get("/login") {}

            get("/oauth/callback") {
                val oauth2 = call.authentication.principal<OAuthAccessTokenResponse.OAuth2>()
                val idToken = oauth2?.extraParameters["id_token"]
                val refreshToken = oauth2?.refreshToken
                when {
                    idToken == null -> call.respond(Unauthorized)
                    refreshToken == null -> call.respond(Unauthorized)
                    else -> {
                        val now = Clock.System.now()
                        val expiresAt = now + oauth2.expiresIn.seconds
                        call.sessions.set(
                            UserSession(
                                idToken,
                                oauth2.accessToken,
                                oauth2.expiresIn,
                                refreshToken,
                                expiresAt,
                            )
                        )
                        call.respondRedirect("/")
                    }
                }
            }
        }

        get("/logout") {
            val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/")
            call.sessions.clear<UserSession>()
            call.respondRedirect(
                URLBuilder(openIdConfig.endSessionEndpoint)
                    .apply {
                        parameters.append("id_token_hint", session.idToken)
                        parameters.append("post_logout_redirect_uri", call.requestUrl("/"))
                    }
                    .buildString()
            )
        }
    }

    attributes[RefresherKey] =
        TokenRefresher(httpClient, openIdConfig.tokenEndpoint, config.clientId, config.clientSecret)
}

fun Route.public(build: Route.() -> Unit): Route =
    authenticate(optional = true) {
        install(
            createRouteScopedPlugin("SecureUserSession") {
                route!!.intercept(ApplicationCallPipeline.Call) {
                    val user = call.principal<UserSession>()
                    if (user == null) proceed() else user.refresh()
                }
            }
        )
        build()
    }

fun Route.userSession(
    build:
        context(UserSessionScope)
        Route.() -> Unit
): Route = authenticate {
    install(
        createRouteScopedPlugin("SecureUserSession") {
            route!!.intercept(ApplicationCallPipeline.Call) {
                val user = call.principal<UserSession>()
                if (user == null) {
                    call.response.headers.append("HX-Redirect", "/login")
                    call.respond(Unauthorized)
                } else {
                    user.refresh()
                }
            }
        }
    )
    with(
        UserSessionScope {
            requireNotNull(contextOf<RoutingContext>().call.sessions.get<UserSession>()) {
                "UserSession not found - route not properly secured with userSession"
            }
        }
    ) {
        build()
    }
}

@Serializable
private data class RefreshTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("id_token") val idToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("token_type") val tokenType: String = "Bearer",
)

private val RefresherKey = AttributeKey<TokenRefresher>("refresher")

private class TokenRefresher(
    private val httpClient: HttpClient,
    private val tokenEndpoint: String,
    private val clientId: String,
    private val clientSecret: String,
) {
    private suspend fun refresh(refreshToken: String): RefreshTokenResponse {
        logger.debug("Refreshing access token")
        return httpClient
            .submitForm(
                url = tokenEndpoint,
                formParameters =
                    parameters {
                        append("grant_type", "refresh_token")
                        append("refresh_token", refreshToken)
                        append("client_id", clientId)
                        append("client_secret", clientSecret)
                    },
            )
            .body()
    }

    suspend fun refreshSession(session: UserSession): UserSession {
        logger.debug("Token expiring soon, refreshing...")
        val response = refresh(session.refreshToken)
        val now = Clock.System.now()
        val refreshedSession =
            session.copy(
                idToken = response.idToken,
                accessToken = response.accessToken,
                expiresIn = response.expiresIn,
                expiresAt = now + response.expiresIn.seconds,
                refreshToken = response.refreshToken ?: session.refreshToken,
            )
        logger.debug("Token refreshed successfully, new expiration: {}", refreshedSession.expiresAt)
        return refreshedSession
    }
}

context(pipeline: PipelineContext<Unit, PipelineCall>)
private suspend fun UserSession.refresh(): Unit =
    if (shouldRefresh()) {
        val session = pipeline.call.application.attributes[RefresherKey].refreshSession(this)
        pipeline.call.sessions.set(session)
        withContext(AuthContext.UserAuth(session.accessToken)) { pipeline.proceed() }
    } else {
        withContext(AuthContext.UserAuth(accessToken)) { pipeline.proceed() }
    }

private fun UserSession.shouldRefresh(bufferSeconds: Long = 60): Boolean =
    Clock.System.now() >= expiresAt - bufferSeconds.seconds

private fun AuthenticationConfig.oauth(
    openIdConfig: OpenIdConfiguration,
    config: Config.Security,
    httpClient: HttpClient,
) =
    oauth("oauth") {
        client = httpClient
        urlProvider = { requestUrl("/oauth/callback") }
        providerLookup = {
            OAuthServerSettings.OAuth2ServerSettings(
                name = "foodies-oauth",
                authorizeUrl = openIdConfig.authorizationEndpoint,
                accessTokenUrl = openIdConfig.tokenEndpoint,
                requestMethod = HttpMethod.Post,
                clientId = config.clientId,
                clientSecret = config.clientSecret,
                defaultScopes =
                    listOf("openid", "profile", "email", "offline_access", "aud-basket-service"),
            )
        }
    }

@Suppress("MagicNumber")
private fun ApplicationCall.requestUrl(callback: String): String {
    val portSuffix =
        when {
            request.origin.scheme == "http" && request.port() == 80 -> ""
            request.origin.scheme == "https" && request.port() == 443 -> ""
            else -> ":${request.port()}"
        }
    return "${request.origin.scheme}://${request.host()}$portSuffix$callback"
}

package io.ktor.foodies.server.security

import com.auth0.jwk.JwkProvider
import io.ktor.client.HttpClient
import io.ktor.foodies.server.Config
import io.ktor.foodies.server.auth.AuthContext
import io.ktor.foodies.server.openid.OpenIdConfiguration
import io.ktor.foodies.server.openid.discover
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.URLBuilder
import io.ktor.http.auth.HttpAuthHeader
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
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelineContext
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds

private val logger = LoggerFactory.getLogger("UserSessionScope")

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

    authentication {
        oauth(openIdConfig, config, httpClient)
        jwt(openIdConfig.jwks(), openIdConfig.issuer)
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
                    val now = Clock.System.now()
                    val expiresAt = now + oauth2.expiresIn.seconds
                    call.sessions.set(
                        UserSession(
                            idToken,
                            oauth2.accessToken,
                            oauth2.expiresIn,
                            oauth2.refreshToken,
                            expiresAt
                        )
                    )
                    call.respondRedirect("/")
                }
            }
        }

        get("/logout") {
            val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/")
            call.sessions.clear<UserSession>()
            call.respondRedirect(URLBuilder(openIdConfig.endSessionEndpoint).apply {
                parameters.append("id_token_hint", session.idToken)
                parameters.append("post_logout_redirect_uri", call.requestUrl("/"))
            }.buildString())
        }
    }

    attributes[RefresherKey] = TokenRefresher(
        httpClient = httpClient,
        tokenEndpoint = openIdConfig.tokenEndpoint,
        clientId = config.clientId,
        clientSecret = config.clientSecret
    )
}

private val RefresherKey = AttributeKey<TokenRefresher>("refresher")

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
    urlProvider = { requestUrl("/oauth/callback") }
    providerLookup = {
        OAuthServerSettings.OAuth2ServerSettings(
            name = "foodies-oauth",
            authorizeUrl = openIdConfig.authorizationEndpoint,
            accessTokenUrl = openIdConfig.tokenEndpoint,
            requestMethod = HttpMethod.Post,
            clientId = config.clientId,
            clientSecret = config.clientSecret,
            defaultScopes = listOf("openid", "profile", "email", "offline_access", "aud-basket-service"),
        )
    }
}

private fun ApplicationCall.requestUrl(callback: String): String {
    val portSuffix = when {
        request.origin.scheme == "http" && request.port() == 80 -> ""
        request.origin.scheme == "https" && request.port() == 443 -> ""
        else -> ":${request.port()}"
    }
    return "${request.origin.scheme}://${request.host()}$portSuffix$callback"
}

fun interface UserSessionScope {
    context(ctx: RoutingContext)
    suspend fun userSession(): UserSession
}

context(scope: UserSessionScope)
suspend fun RoutingContext.userSession(): UserSession = scope.userSession()

fun Route.public(build: Route.() -> Unit): Route = authenticate(optional = true) {
    install(createRouteScopedPlugin("SecureUserSession") {
        route!!.intercept(ApplicationCallPipeline.Call) {
            val user = call.principal<UserSession>()
            if (user == null) {
                proceed()
            } else {
                val session = call.application.attributes[RefresherKey].refreshSessionIfNeeded(user)
                if (session == null) {
                    call.sessions.clear<UserSession>()
                    proceed()
                } else {
                    withContext(AuthContext.UserAuth(session.accessToken)) { proceed() }
                }
            }
        }
    })
    build()
}

fun Route.userSession(build: context(UserSessionScope) Route.() -> Unit): Route =
    authenticate {
        install(createRouteScopedPlugin("SecureUserSession") {
            route!!.intercept(ApplicationCallPipeline.Call) {
                val principal = call.principal<UserSession>()
                if (principal == null) {
                    call.response.headers.append("HX-Redirect", "/login")
                    call.respond(Unauthorized)
                    return@intercept
                }

                if (principal.isExpired() && principal.refreshToken == null) {
                    logger.info("Session expired and no refresh token available, redirecting to login")
                    call.response.headers.append("HX-Redirect", "/login")
                    call.respond(Unauthorized)
                    return@intercept
                }

                val session = call.application.attributes[RefresherKey].refreshSessionIfNeeded(principal)
                if (session == null) {
                    call.sessions.clear<UserSession>()
                    call.response.headers.append("HX-Redirect", "/login")
                    call.respond(Unauthorized)
                    return@intercept
                }
                withContext(AuthContext.UserAuth(session.accessToken)) { proceed() }
            }
        })
        with(UserSessionScope {
            requireNotNull(contextOf<RoutingContext>().call.sessions.get<UserSession>()) {
                "UserSession not found - route not properly secured with userSession"
            }
        }) {
            build()
        }
    }

context(pipeline: PipelineContext<*, PipelineCall>)
private suspend fun TokenRefresher.refreshSessionIfNeeded(session: UserSession): UserSession? {
    if (!session.shouldRefresh()) {
        return session
    }

    val refreshToken = session.refreshToken
    if (refreshToken == null) {
        logger.info("Token refresh required but no refresh token available")
        return null
    }

    return try {
        logger.debug("Token expiring soon, refreshing...")
        val response = refresh(refreshToken)
        val refreshedSession = session.withRefreshedTokens(response)
        pipeline.call.sessions.set(refreshedSession)
        logger.debug("Token refreshed successfully, new expiration: {}", refreshedSession.expiresAt)
        refreshedSession
    } catch (e: Exception) {
        logger.warn("Failed to refresh token: ${e.message}")
        null
    }
}

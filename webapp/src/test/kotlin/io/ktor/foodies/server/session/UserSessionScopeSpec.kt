package io.ktor.foodies.server.session

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.foodies.server.ServiceContext
import io.ktor.foodies.server.security.UserSession
import io.ktor.foodies.server.security.userSession
import io.ktor.foodies.server.serviceContext
import io.ktor.foodies.server.test.ctxSuite
import io.ktor.foodies.server.testWebAppService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Serializable
private data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("id_token")
    val idToken: String,
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("refresh_token")
    val refreshToken: String
)

@Serializable
private data class SessionPayload(
    val idToken: String,
    val accessToken: String,
    val expiresIn: Long,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long
)

context(ctx: ServiceContext)
private suspend fun fetchTokens(username: String, password: String): TokenResponse {
    val tokenEndpoint = "${ctx.keycloakContainer().authServerUrl}/realms/foodies-keycloak/protocol/openid-connect/token"
    val client = HttpClient(Apache5) { install(ContentNegotiation) { json() } }
    return client.use {
        it.submitForm(
            url = tokenEndpoint,
            formParameters = parameters {
                append("grant_type", "password")
                append("client_id", "foodies")
                append("client_secret", "foodies_client_secret")
                append("username", username)
                append("password", password)
                append("scope", "openid profile email offline_access")
            }
        ).body()
    }
}

val userSessionScopeSpec by ctxSuite(context = { serviceContext() }, testConfig = TestConfig.testScope(true)) {
    testWebAppService("returns 401 and HX-Redirect when no session") {
        application {
            routing {
                userSession {
                    get("/protected") { call.respondText("OK") }
                }
            }
        }

        val response = client.get("/protected")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("/login", response.headers["HX-Redirect"])
    }

    testWebAppService("returns 200 and session data when session exists") {
        val tokens = fetchTokens("food3_lover", "password")
        val expiresAt = Clock.System.now() + tokens.expiresIn.seconds

        application {
            routing {
                post("/test/session") {
                    val payload = call.receive<SessionPayload>()
                    call.sessions.set(
                        UserSession(
                            idToken = payload.idToken,
                            accessToken = payload.accessToken,
                            expiresIn = payload.expiresIn,
                            refreshToken = payload.refreshToken,
                            expiresAt = Instant.fromEpochSeconds(payload.expiresAtEpochSeconds)
                        )
                    )
                    call.respondText("Session set")
                }
                route("/protected") {
                    userSession {
                        get {
                            val session = userSession()
                            call.respondText("Hello ${session.accessToken}")
                        }
                    }
                }
            }
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
            install(HttpCookies)
        }

        val setResp = client.post("/test/session") {
            contentType(ContentType.Application.Json)
            setBody(
                SessionPayload(
                    idToken = tokens.idToken,
                    accessToken = tokens.accessToken,
                    expiresIn = tokens.expiresIn,
                    refreshToken = tokens.refreshToken,
                    expiresAtEpochSeconds = expiresAt.epochSeconds
                )
            )
        }
        assertEquals(HttpStatusCode.OK, setResp.status)

        val response = client.get("/protected")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello ${tokens.accessToken}", response.bodyAsText())
    }

    testWebAppService("refreshes tokens when session is near expiry") {
        val tokens = fetchTokens("food3_lover", "password")
        val expiresAt = Clock.System.now() - 1.seconds

        application {
            routing {
                post("/test/session") {
                    val payload = call.receive<SessionPayload>()
                    call.sessions.set(
                        UserSession(
                            idToken = payload.idToken,
                            accessToken = payload.accessToken,
                            expiresIn = payload.expiresIn,
                            refreshToken = payload.refreshToken,
                            expiresAt = Instant.fromEpochSeconds(payload.expiresAtEpochSeconds)
                        )
                    )
                    call.respondText("Session set")
                }
                route("/protected") {
                    userSession {
                        get {
                            val session = userSession()
                            call.respondText(session.accessToken)
                        }
                    }
                }
            }
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
            install(HttpCookies)
        }

        val setResp = client.post("/test/session") {
            contentType(ContentType.Application.Json)
            setBody(
                SessionPayload(
                    idToken = tokens.idToken,
                    accessToken = tokens.accessToken,
                    expiresIn = tokens.expiresIn,
                    refreshToken = tokens.refreshToken,
                    expiresAtEpochSeconds = expiresAt.epochSeconds
                )
            )
        }
        assertEquals(HttpStatusCode.OK, setResp.status)

        val response = client.get("/protected")
        assertEquals(HttpStatusCode.OK, response.status)
        assertNotEquals(tokens.accessToken, response.bodyAsText())
    }
}

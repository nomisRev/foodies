package io.ktor.foodies.server.session

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.foodies.server.security.UserSession
import io.ktor.foodies.server.security.jwt
import io.ktor.foodies.server.security.userSession
import io.ktor.foodies.server.security.withUserSession
import io.ktor.foodies.server.test.testApplication
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.Authentication
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.sessions.SessionStorageMemory
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import kotlin.test.assertEquals

private class TestJwkProvider : JwkProvider {
    val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    val publicKey = keyPair.public as RSAPublicKey
    val privateKey = keyPair.private as RSAPrivateKey

    override fun get(keyId: String): Jwk =
        Jwk.fromValues(
            mapOf(
                "kty" to "RSA",
                "kid" to keyId,
                "n" to
                    java.util.Base64.getUrlEncoder()
                        .encodeToString(publicKey.modulus.toByteArray()),
                "e" to
                    java.util.Base64.getUrlEncoder()
                        .encodeToString(publicKey.publicExponent.toByteArray()),
                "alg" to "RS256",
                "use" to "sig",
            )
        )

    val token =
        JWT.create()
            .withKeyId("test-key")
            .withIssuer("issuer")
            .withAudience("foodies")
            .sign(Algorithm.RSA256(publicKey, privateKey))
}

val userSessionScopeSpec by testSuite {
    val provider = TestJwkProvider()

    testApplication("returns 401 and HX-Redirect when no session") {
        install(Authentication) { jwt(provider, "issuer") }
        install(Sessions) { cookie<UserSession>("USER_SESSION", SessionStorageMemory()) }
        routing {
            withUserSession {
                get("/protected") {
                    println("/protected before userSession()")
                    println("Handling /protected request. ${userSession()}")
                    call.respondText("OK")
                }
            }
        }

        val response = client.get("/protected")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("/login", response.headers["HX-Redirect"])
    }

    testApplication("returns 200 and session data when session exists") {
        install(Authentication) { jwt(provider, "issuer") }
        install(Sessions) { cookie<UserSession>("USER_SESSION", SessionStorageMemory()) }
        routing {
            get("/set-session") {
                call.sessions.set(
                    UserSession(idToken = provider.token, "test-token", expiresIn = 10000, null)
                )
                call.respondText("Session set")
            }
            route("/protected") {
                withUserSession {
                    get {
                        val session = userSession()
                        call.respondText("Hello ${session.accessToken}")
                    }
                }
            }
        }

        val client = createClient { install(HttpCookies) }

        val setResp = client.get("/set-session")
        assertEquals(HttpStatusCode.OK, setResp.status)

        val response = client.get("/protected")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello test-token", response.bodyAsText())
    }
}

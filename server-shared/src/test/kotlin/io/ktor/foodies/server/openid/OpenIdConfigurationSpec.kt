package io.ktor.foodies.server.openid

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.server.test.jsonClient
import io.ktor.foodies.server.test.testApplication
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val discoveryPayload = """
{
  "issuer": "/issuer",
  "authorization_endpoint": "https://issuer.local/auth",
  "token_endpoint": "https://issuer.local/token",
  "jwks_uri": "https://issuer.local/jwks",
  "end_session_endpoint": "https://issuer.local/logout"
}
""".trimIndent()

val openIdConfigurationSpec by testSuite {
    testApplication("discover parses openid metadata from well-known endpoint") {
        application {
            routing {
                get("/issuer/.well-known/openid-configuration") {
                    call.respondText(discoveryPayload, contentType = ContentType.Application.Json)
                }
            }
        }

        val discovered = jsonClient().discover("/issuer")

        assertEquals("/issuer", discovered.issuer)
        assertEquals("https://issuer.local/auth", discovered.authorizationEndpoint)
        assertEquals("https://issuer.local/token", discovered.tokenEndpoint)
        assertEquals("https://issuer.local/jwks", discovered.jwksUri)
        assertEquals("https://issuer.local/logout", discovered.endSessionEndpoint)
    }

    testApplication("discover throws on non-success response") {
        application {
            routing {
                get("/issuer/.well-known/openid-configuration") {
                    call.respondText("boom", status = HttpStatusCode.BadGateway)
                }
            }
        }

        val exception = assertFailsWith<IllegalStateException> {
            jsonClient().discover("/issuer")
        }

        assertTrue(exception.message?.contains("Failed to discover OpenId configuration") == true)
        assertTrue(exception.message?.contains("502 Bad Gateway") == true)
    }

    test("jwks provider can be created from configuration uri") {
        val config = OpenIdConfiguration(
            issuer = "https://issuer.local",
            authorizationEndpoint = "https://issuer.local/auth",
            tokenEndpoint = "https://issuer.local/token",
            jwksUri = "https://issuer.local/jwks",
            endSessionEndpoint = "https://issuer.local/logout"
        )

        val provider = config.jwks()

        assertNotNull(provider)
    }
}

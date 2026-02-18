package io.ktor.foodies.server.openid

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.foodies.server.test.authTest
import io.ktor.foodies.server.test.createUserToken
import io.ktor.foodies.server.auth.secureUser
import io.ktor.foodies.server.auth.userPrincipal
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val securitySpec by testSuite {
    authTest("user JWT validation extracts userId from subject") { config ->
        routing {
            secureUser {
                get("/user") {
                    val principal = userPrincipal()
                    call.respondText(principal.userId)
                }
            }
        }

        val token = createUserToken(config, userId = "user-456")
        val response = client.get("/user") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("user-456", response.bodyAsText())
    }

    authTest("user JWT validation extracts email claim") { config ->
        routing {
            secureUser {
                get("/user") {
                    val principal = userPrincipal()
                    call.respondText(principal.email ?: "no-email")
                }
            }
        }

        val token = createUserToken(config, email = "admin@foodies.com")
        val response = client.get("/user") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("admin@foodies.com", response.bodyAsText())
    }

    authTest("user JWT validation extracts roles from realm_access") { config ->
        routing {
            secureUser {
                get("/user") {
                    val principal = userPrincipal()
                    call.respondText(principal.roles.sorted().joinToString(","))
                }
            }
        }

        val token = createUserToken(config, roles = listOf("user", "admin", "moderator"))
        val response = client.get("/user") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("admin,moderator,user", response.bodyAsText())
    }

    authTest("user JWT validation rejects token without email") { config ->
        routing {
            secureUser {
                get("/user") {
                    call.respondText("Success")
                }
            }
        }

        val tokenWithoutEmail = com.auth0.jwt.JWT.create()
            .withSubject("user-no-email")
            .withClaim("realm_access", mapOf("roles" to listOf("user")))
            .withAudience(config.audience)
            .withIssuer(config.issuer)
            .withExpiresAt(java.util.Date(System.currentTimeMillis() + 3600000))
            .sign(config.algorithm)

        val response = client.get("/user") {
            header(HttpHeaders.Authorization, "Bearer $tokenWithoutEmail")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    authTest("user JWT validation stores access token") { config ->
        routing {
            secureUser {
                get("/user") {
                    val principal = userPrincipal()
                    call.respondText("Token length: ${principal.accessToken.length}")
                }
            }
        }

        val token = createUserToken(config)
        val response = client.get("/user") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().startsWith("Token length:"))
    }
}

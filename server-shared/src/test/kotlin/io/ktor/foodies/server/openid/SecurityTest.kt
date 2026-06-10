package io.ktor.foodies.server.openid

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.foodies.server.auth.secure
import io.ktor.foodies.server.test.*
import io.ktor.http.*
import io.ktor.server.auth.typesafe.principal
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalKtorApi::class)
val securitySpec by testSuite {
    authTest("user JWT validation extracts userId from subject") { config ->
        routing {
            secure {
                get("/user") {
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
            secure {
                get("/user") {
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
            secure {
                get("/user") {
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
            secure {
                get("/user") {
                    call.respondText("Success")
                }
            }
        }

        val tokenWithoutEmail = config.keys.accessToken(config.issuer, config.audience) {
            subject = "user-no-email"
            claim("realm_access", mapOf("roles" to listOf("user")))
        }

        val response = client.get("/user") {
            header(HttpHeaders.Authorization, "Bearer $tokenWithoutEmail")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    authTest("user JWT validation stores access token") { config ->
        routing {
            secure {
                get("/user") {
                    call.respondText("Token length: ${principal.accessToken.value.length}")
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

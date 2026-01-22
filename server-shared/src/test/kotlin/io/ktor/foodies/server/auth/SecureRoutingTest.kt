package io.ktor.foodies.server.auth

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.foodies.server.test.authTest
import io.ktor.foodies.server.test.createServiceToken
import io.ktor.foodies.server.test.createUserToken
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.currentCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

val secureRoutingSpec by testSuite {
    authTest("secureUser should authenticate valid user token") { config ->
        application {
            routing {
                secureUser {
                    get("/protected") {
                        val user = userPrincipal()
                        call.respondText("Hello ${user.userId}")
                    }
                }
            }
        }

        val token = createUserToken(config)
        val response = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello user-123", response.bodyAsText())
    }

    authTest("secureUser should reject request without token") { config ->
        routing {
            secureUser {
                get("/protected") {
                    call.respondText("Secret")
                }
            }
        }

        val response = client.get("/protected")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    authTest("secureUser should establish UserAuth context") { config ->
        routing {
            secureUser {
                get("/context") {
                    val authContext = currentCoroutineContext()[AuthContext]
                    assertNotNull(authContext)
                    assertIs<AuthContext.UserAuth>(authContext)
                    call.respondText("Context: ${authContext.accessToken.take(10)}")
                }
            }
        }

        val token = createUserToken(config)
        val response = client.get("/context") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    authTest("secureService should authenticate valid service token") { config ->
        routing {
            secureService {
                get("/service-endpoint") {
                    val service = servicePrincipal()
                    call.respondText("Service: ${service.clientId}")
                }
            }
        }

        val token =
            createServiceToken(config, clientId = "order-service")
        val response = client.get("/service-endpoint") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Service: order-service", response.bodyAsText())
    }

    authTest("secureService should enforce required roles") { config ->
        routing {
            secureService("service:write") {
                get("/write-endpoint") {
                    call.respondText("Write allowed")
                }
            }
        }

        val tokenWithoutRole = createServiceToken(
            config,
            clientId = "test-service",
            roles = listOf("service:read")
        )
        val response = client.get("/write-endpoint") {
            header(HttpHeaders.Authorization, "Bearer $tokenWithoutRole")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    authTest("secureService should allow access when all required roles present") { config ->
        routing {
            secureService("service:read", "service:write") {
                get("/rw-endpoint") {
                    call.respondText("Full access")
                }
            }
        }

        val tokenWithBothRoles = createServiceToken(
            config,
            clientId = "admin-service",
            roles = listOf("service:read", "service:write", "service:admin")
        )
        val response = client.get("/rw-endpoint") {
            header(HttpHeaders.Authorization, "Bearer $tokenWithBothRoles")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Full access", response.bodyAsText())
    }

    authTest("secureService should establish ServiceAuth context with X-User-Context header") { config ->
        routing {
            secureService {
                get("/context") {
                    val authContext = currentCoroutineContext()[AuthContext]
                    assertNotNull(authContext)
                    assertIs<AuthContext.ServiceAuth>(authContext)
                    call.respondText("User: ${authContext.userToken?.take(10) ?: "none"}")
                }
            }
        }

        val serviceToken = createServiceToken(config)
        val userToken = createUserToken(config)
        val response = client.get("/context") {
            header(HttpHeaders.Authorization, "Bearer $serviceToken")
            header("X-User-Context", "Bearer $userToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    authTest("userPrincipal extension should return principal in secureUser route") { config ->
        routing {
            secureUser {
                get("/me") {
                    val principal = userPrincipal()
                    call.respondText("${principal.userId}:${principal.email}:${principal.roles.size}")
                }
            }
        }

        val token = createUserToken(
            config,
            userId = "user-456",
            email = "admin@example.com",
            roles = listOf("user", "admin")
        )
        val response = client.get("/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("user-456:admin@example.com:2", response.bodyAsText())
    }

    authTest("servicePrincipal extension should return principal in secureService route") { config ->
        routing {
            secureService {
                get("/info") {
                    val principal = servicePrincipal()
                    call.respondText("${principal.clientId}:${principal.roles.size}")
                }
            }
        }

        val token = createServiceToken(
            config,
            clientId = "basket-service",
            roles = listOf("service:read", "service:write")
        )
        val response = client.get("/info") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("basket-service:2", response.bodyAsText())
    }
}

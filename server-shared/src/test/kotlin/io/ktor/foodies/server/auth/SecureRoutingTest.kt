package io.ktor.foodies.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.currentCoroutineContext
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

val secureRoutingSpec by testSuite {

    val algorithm = Algorithm.HMAC256("test-secret")

    fun createUserToken(userId: String = "user-123", email: String = "test@example.com", roles: List<String> = listOf("user")): String {
        return JWT.create()
            .withSubject(userId)
            .withClaim("email", email)
            .withClaim("realm_access", mapOf("roles" to roles))
            .withAudience("foodies")
            .withIssuer("http://test-issuer")
            .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
            .sign(algorithm)
    }

    fun createServiceToken(clientId: String = "test-service", roles: List<String> = listOf("service:read")): String {
        return JWT.create()
            .withSubject("service-account-$clientId")
            .withClaim("azp", clientId)
            .withClaim("realm_access", mapOf("roles" to roles))
            .withAudience("foodies")
            .withIssuer("http://test-issuer")
            .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
            .sign(algorithm)
    }

    test("secureUser should authenticate valid user token") {
        testApplication {
            application {
                install(Authentication) {
                    jwt("user") {
                        verifier(JWT.require(algorithm).withIssuer("http://test-issuer").build())
                        validate { credential ->
                            val email = credential.payload.getClaim("email").asString()
                            val authHeader = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ") ?: ""
                            if (email != null) {
                                val roles = credential.payload.getClaim("realm_access")
                                    .asMap()["roles"] as? List<*> ?: emptyList<String>()
                                UserPrincipal(
                                    userId = credential.payload.subject,
                                    email = email,
                                    roles = roles.filterIsInstance<String>().toSet(),
                                    accessToken = authHeader
                                )
                            } else null
                        }
                    }
                }
                routing {
                    secureUser {
                        get("/protected") {
                            val user = userPrincipal()
                            call.respondText("Hello ${user.userId}")
                        }
                    }
                }
            }

            val token = createUserToken()
            val response = client.get("/protected") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Hello user-123", response.bodyAsText())
        }
    }

    test("secureUser should reject request without token") {
        testApplication {
            application {
                install(Authentication) {
                    jwt("user") {
                        verifier(JWT.require(algorithm).withIssuer("http://test-issuer").build())
                        validate { credential ->
                            val email = credential.payload.getClaim("email").asString()
                            val authHeader = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ") ?: ""
                            if (email != null) {
                                UserPrincipal(
                                    userId = credential.payload.subject,
                                    email = email,
                                    roles = emptySet(),
                                    accessToken = authHeader
                                )
                            } else null
                        }
                    }
                }
                routing {
                    secureUser {
                        get("/protected") {
                            call.respondText("Secret")
                        }
                    }
                }
            }

            val response = client.get("/protected")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    test("secureUser should establish UserAuth context") {
        testApplication {
            application {
                install(Authentication) {
                    jwt("user") {
                        verifier(JWT.require(algorithm).withIssuer("http://test-issuer").build())
                        validate { credential ->
                            val email = credential.payload.getClaim("email").asString()
                            val authHeader = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ") ?: ""
                            if (email != null) {
                                UserPrincipal(
                                    userId = credential.payload.subject,
                                    email = email,
                                    roles = emptySet(),
                                    accessToken = authHeader
                                )
                            } else null
                        }
                    }
                }
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
            }

            val token = createUserToken()
            val response = client.get("/context") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    test("secureService should authenticate valid service token") {
        testApplication {
            application {
                install(Authentication) {
                    jwt("service") {
                        verifier(JWT.require(algorithm).withIssuer("http://test-issuer").build())
                        validate { credential ->
                            val clientId = credential.payload.getClaim("azp").asString()
                            if (clientId?.endsWith("-service") == true) {
                                val roles = credential.payload.getClaim("realm_access")
                                    .asMap()["roles"] as? List<*> ?: emptyList<String>()
                                ServicePrincipal(
                                    serviceAccountId = credential.payload.subject,
                                    clientId = clientId,
                                    roles = roles.filterIsInstance<String>().toSet()
                                )
                            } else null
                        }
                    }
                }
                routing {
                    secureService {
                        get("/service-endpoint") {
                            val service = servicePrincipal()
                            call.respondText("Service: ${service.clientId}")
                        }
                    }
                }
            }

            val token = createServiceToken(clientId = "order-service")
            val response = client.get("/service-endpoint") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Service: order-service", response.bodyAsText())
        }
    }

    test("secureService should enforce required roles") {
        testApplication {
            application {
                install(Authentication) {
                    jwt("service") {
                        verifier(JWT.require(algorithm).withIssuer("http://test-issuer").build())
                        validate { credential ->
                            val clientId = credential.payload.getClaim("azp").asString()
                            if (clientId?.endsWith("-service") == true) {
                                val roles = credential.payload.getClaim("realm_access")
                                    .asMap()["roles"] as? List<*> ?: emptyList<String>()
                                ServicePrincipal(
                                    serviceAccountId = credential.payload.subject,
                                    clientId = clientId,
                                    roles = roles.filterIsInstance<String>().toSet()
                                )
                            } else null
                        }
                    }
                }
                routing {
                    secureService("service:write") {
                        get("/write-endpoint") {
                            call.respondText("Write allowed")
                        }
                    }
                }
            }

            val tokenWithoutRole = createServiceToken(clientId = "test-service", roles = listOf("service:read"))
            val response = client.get("/write-endpoint") {
                header(HttpHeaders.Authorization, "Bearer $tokenWithoutRole")
            }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    test("secureService should allow access when all required roles present") {
        testApplication {
            application {
                install(Authentication) {
                    jwt("service") {
                        verifier(JWT.require(algorithm).withIssuer("http://test-issuer").build())
                        validate { credential ->
                            val clientId = credential.payload.getClaim("azp").asString()
                            if (clientId?.endsWith("-service") == true) {
                                val roles = credential.payload.getClaim("realm_access")
                                    .asMap()["roles"] as? List<*> ?: emptyList<String>()
                                ServicePrincipal(
                                    serviceAccountId = credential.payload.subject,
                                    clientId = clientId,
                                    roles = roles.filterIsInstance<String>().toSet()
                                )
                            } else null
                        }
                    }
                }
                routing {
                    secureService("service:read", "service:write") {
                        get("/rw-endpoint") {
                            call.respondText("Full access")
                        }
                    }
                }
            }

            val tokenWithBothRoles = createServiceToken(
                clientId = "admin-service",
                roles = listOf("service:read", "service:write", "service:admin")
            )
            val response = client.get("/rw-endpoint") {
                header(HttpHeaders.Authorization, "Bearer $tokenWithBothRoles")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Full access", response.bodyAsText())
        }
    }

    test("secureService should establish ServiceAuth context with X-User-Context header") {
        testApplication {
            application {
                install(Authentication) {
                    jwt("service") {
                        verifier(JWT.require(algorithm).withIssuer("http://test-issuer").build())
                        validate { credential ->
                            val clientId = credential.payload.getClaim("azp").asString()
                            if (clientId?.endsWith("-service") == true) {
                                ServicePrincipal(
                                    serviceAccountId = credential.payload.subject,
                                    clientId = clientId,
                                    roles = emptySet()
                                )
                            } else null
                        }
                    }
                }
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
            }

            val serviceToken = createServiceToken()
            val userToken = createUserToken()
            val response = client.get("/context") {
                header(HttpHeaders.Authorization, "Bearer $serviceToken")
                header("X-User-Context", "Bearer $userToken")
            }

            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    test("userPrincipal extension should return principal in secureUser route") {
        testApplication {
            application {
                install(Authentication) {
                    jwt("user") {
                        verifier(JWT.require(algorithm).withIssuer("http://test-issuer").build())
                        validate { credential ->
                            val email = credential.payload.getClaim("email").asString()
                            val authHeader = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ") ?: ""
                            if (email != null) {
                                UserPrincipal(
                                    userId = credential.payload.subject,
                                    email = email,
                                    roles = setOf("user", "admin"),
                                    accessToken = authHeader
                                )
                            } else null
                        }
                    }
                }
                routing {
                    secureUser {
                        get("/me") {
                            val principal = userPrincipal()
                            call.respondText("${principal.userId}:${principal.email}:${principal.roles.size}")
                        }
                    }
                }
            }

            val token = createUserToken(userId = "user-456", email = "admin@example.com", roles = listOf("user", "admin"))
            val response = client.get("/me") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("user-456:admin@example.com:2", response.bodyAsText())
        }
    }

    test("servicePrincipal extension should return principal in secureService route") {
        testApplication {
            application {
                install(Authentication) {
                    jwt("service") {
                        verifier(JWT.require(algorithm).withIssuer("http://test-issuer").build())
                        validate { credential ->
                            val clientId = credential.payload.getClaim("azp").asString()
                            if (clientId?.endsWith("-service") == true) {
                                val roles = credential.payload.getClaim("realm_access")
                                    .asMap()["roles"] as? List<*> ?: emptyList<String>()
                                ServicePrincipal(
                                    serviceAccountId = credential.payload.subject,
                                    clientId = clientId,
                                    roles = roles.filterIsInstance<String>().toSet()
                                )
                            } else null
                        }
                    }
                }
                routing {
                    secureService {
                        get("/info") {
                            val principal = servicePrincipal()
                            call.respondText("${principal.clientId}:${principal.roles.size}")
                        }
                    }
                }
            }

            val token = createServiceToken(clientId = "basket-service", roles = listOf("service:read", "service:write"))
            val response = client.get("/info") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("basket-service:2", response.bodyAsText())
        }
    }
}

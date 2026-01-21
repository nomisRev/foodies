package io.ktor.foodies.server.openid

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.foodies.server.auth.ServicePrincipal
import io.ktor.foodies.server.auth.UserPrincipal
import io.ktor.foodies.server.auth.secureService
import io.ktor.foodies.server.auth.secureUser
import io.ktor.foodies.server.auth.servicePrincipal
import io.ktor.foodies.server.auth.userPrincipal
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val securitySpec by testSuite {

    val algorithm = Algorithm.HMAC256("test-secret")
    val testIssuer = "http://test-issuer"

    fun createUserJwt(
        userId: String = "test-user-123",
        email: String = "test@example.com",
        roles: List<String> = listOf("user")
    ): String {
        return JWT.create()
            .withSubject(userId)
            .withClaim("email", email)
            .withClaim("realm_access", mapOf("roles" to roles))
            .withAudience("foodies")
            .withIssuer(testIssuer)
            .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
            .sign(algorithm)
    }

    fun createServiceJwt(
        serviceAccountId: String = "service-account-order-service",
        clientId: String = "order-service",
        roles: List<String> = listOf("service:basket:read")
    ): String {
        return JWT.create()
            .withSubject(serviceAccountId)
            .withClaim("azp", clientId)
            .withClaim("realm_access", mapOf("roles" to roles))
            .withAudience("foodies")
            .withIssuer(testIssuer)
            .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
            .sign(algorithm)
    }

    fun createServiceJwtWithClientIdClaim(
        serviceAccountId: String = "service-account-payment-service",
        clientId: String = "payment-service",
        roles: List<String> = listOf("service:order:read")
    ): String {
        return JWT.create()
            .withSubject(serviceAccountId)
            .withClaim("client_id", clientId)
            .withClaim("realm_access", mapOf("roles" to roles))
            .withAudience("foodies")
            .withIssuer(testIssuer)
            .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
            .sign(algorithm)
    }

    test("user JWT validation extracts userId from subject") {
        testApplication {
            application {
                setupTestSecurity(algorithm, testIssuer)
                routing {
                    secureUser {
                        get("/user") {
                            val principal = call.userPrincipal()
                            call.respondText(principal.userId)
                        }
                    }
                }
            }

            val token = createUserJwt(userId = "user-456")
            val response = client.get("/user") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("user-456", response.bodyAsText())
        }
    }

    test("user JWT validation extracts email claim") {
        testApplication {
            application {
                setupTestSecurity(algorithm, testIssuer)
                routing {
                    secureUser {
                        get("/user") {
                            val principal = call.userPrincipal()
                            call.respondText(principal.email ?: "no-email")
                        }
                    }
                }
            }

            val token = createUserJwt(email = "admin@foodies.com")
            val response = client.get("/user") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("admin@foodies.com", response.bodyAsText())
        }
    }

    test("user JWT validation extracts roles from realm_access") {
        testApplication {
            application {
                setupTestSecurity(algorithm, testIssuer)
                routing {
                    secureUser {
                        get("/user") {
                            val principal = call.userPrincipal()
                            call.respondText(principal.roles.sorted().joinToString(","))
                        }
                    }
                }
            }

            val token = createUserJwt(roles = listOf("user", "admin", "moderator"))
            val response = client.get("/user") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("admin,moderator,user", response.bodyAsText())
        }
    }

    test("user JWT validation rejects token without email") {
        testApplication {
            application {
                setupTestSecurity(algorithm, testIssuer)
                routing {
                    secureUser {
                        get("/user") {
                            call.respondText("Success")
                        }
                    }
                }
            }

            val tokenWithoutEmail = JWT.create()
                .withSubject("user-no-email")
                .withClaim("realm_access", mapOf("roles" to listOf("user")))
                .withAudience("foodies")
                .withIssuer(testIssuer)
                .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
                .sign(algorithm)

            val response = client.get("/user") {
                header(HttpHeaders.Authorization, "Bearer $tokenWithoutEmail")
            }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    test("user JWT validation stores access token") {
        testApplication {
            application {
                setupTestSecurity(algorithm, testIssuer)
                routing {
                    secureUser {
                        get("/user") {
                            val principal = call.userPrincipal()
                            call.respondText("Token length: ${principal.accessToken.length}")
                        }
                    }
                }
            }

            val token = createUserJwt()
            val response = client.get("/user") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().startsWith("Token length:"))
        }
    }

    test("service JWT validation extracts serviceAccountId from subject") {
        testApplication {
            application {
                setupTestSecurity(algorithm, testIssuer)
                routing {
                    secureService {
                        get("/service") {
                            val principal = call.servicePrincipal()
                            call.respondText(principal.serviceAccountId)
                        }
                    }
                }
            }

            val token = createServiceJwt(serviceAccountId = "service-account-basket-service")
            val response = client.get("/service") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("service-account-basket-service", response.bodyAsText())
        }
    }

    test("service JWT validation extracts clientId from azp claim") {
        testApplication {
            application {
                setupTestSecurity(algorithm, testIssuer)
                routing {
                    secureService {
                        get("/service") {
                            val principal = call.servicePrincipal()
                            call.respondText(principal.clientId)
                        }
                    }
                }
            }

            val token = createServiceJwt(clientId = "basket-service")
            val response = client.get("/service") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("basket-service", response.bodyAsText())
        }
    }

    test("service JWT validation extracts clientId from client_id claim as fallback") {
        testApplication {
            application {
                setupTestSecurity(algorithm, testIssuer)
                routing {
                    secureService {
                        get("/service") {
                            val principal = call.servicePrincipal()
                            call.respondText(principal.clientId)
                        }
                    }
                }
            }

            val token = createServiceJwtWithClientIdClaim(clientId = "payment-service")
            val response = client.get("/service") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("payment-service", response.bodyAsText())
        }
    }

    test("service JWT validation requires clientId ending with -service") {
        testApplication {
            application {
                setupTestSecurity(algorithm, testIssuer)
                routing {
                    secureService {
                        get("/service") {
                            call.respondText("Success")
                        }
                    }
                }
            }

            val invalidServiceToken = JWT.create()
                .withSubject("invalid-service-account")
                .withClaim("azp", "not-a-service-client")
                .withClaim("realm_access", mapOf("roles" to listOf("service:read")))
                .withAudience("foodies")
                .withIssuer(testIssuer)
                .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
                .sign(algorithm)

            val response = client.get("/service") {
                header(HttpHeaders.Authorization, "Bearer $invalidServiceToken")
            }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    test("service JWT validation extracts roles from realm_access") {
        testApplication {
            application {
                setupTestSecurity(algorithm, testIssuer)
                routing {
                    secureService {
                        get("/service") {
                            val principal = call.servicePrincipal()
                            call.respondText(principal.roles.sorted().joinToString(","))
                        }
                    }
                }
            }

            val token = createServiceJwt(
                roles = listOf("service:basket:read", "service:basket:write", "service:menu:read")
            )
            val response = client.get("/service") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("service:basket:read,service:basket:write,service:menu:read", response.bodyAsText())
        }
    }

    test("service JWT validation handles missing realm_access gracefully") {
        testApplication {
            application {
                setupTestSecurity(algorithm, testIssuer)
                routing {
                    secureService {
                        get("/service") {
                            val principal = call.servicePrincipal()
                            call.respondText("Roles: ${principal.roles.size}")
                        }
                    }
                }
            }

            val tokenWithoutRoles = JWT.create()
                .withSubject("service-account-minimal-service")
                .withClaim("azp", "minimal-service")
                .withAudience("foodies")
                .withIssuer(testIssuer)
                .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
                .sign(algorithm)

            val response = client.get("/service") {
                header(HttpHeaders.Authorization, "Bearer $tokenWithoutRoles")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Roles: 0", response.bodyAsText())
        }
    }
}

private fun Application.setupTestSecurity(
    algorithm: Algorithm,
    issuer: String
) {
    install(Authentication) {
        jwt("user") {
            verifier(JWT.require(algorithm).withIssuer(issuer).build())
            validate { credential: JWTCredential ->
                val payload = credential.payload
                val email = payload.getClaim("email").asString()
                val authHeader = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ") ?: ""
                if (email != null) {
                    val roles = payload.getClaim("realm_access")
                        ?.asMap()?.get("roles") as? List<*> ?: emptyList<String>()
                    UserPrincipal(
                        userId = payload.subject,
                        email = email,
                        roles = roles.filterIsInstance<String>().toSet(),
                        accessToken = authHeader
                    )
                } else null
            }
        }

        jwt("service") {
            verifier(JWT.require(algorithm).withIssuer(issuer).build())
            validate { credential: JWTCredential ->
                val payload = credential.payload
                val clientId = payload.getClaim("azp").asString()
                    ?: payload.getClaim("client_id").asString()
                if (clientId?.endsWith("-service") == true) {
                    val roles = payload.getClaim("realm_access")
                        ?.asMap()?.get("roles") as? List<*> ?: emptyList<String>()
                    ServicePrincipal(
                        serviceAccountId = payload.subject,
                        clientId = clientId,
                        roles = roles.filterIsInstance<String>().toSet()
                    )
                } else null
            }
        }
    }
}

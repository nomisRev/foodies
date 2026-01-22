package io.ktor.foodies.order

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.foodies.server.auth.UserPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.assertEquals

private const val TEST_SECRET = "test-secret"

val adminRoutesSpec by testSuite {
    test("should return 403 Forbidden when user is not an admin") {
        val orderService = createTestContext().service

        testApplication {
            application {
                install(ContentNegotiation) { json() }
                install(Authentication) {
                    jwt("user") {
                        verifier(JWT.require(Algorithm.HMAC256(TEST_SECRET)).build())
                        validate { credential ->
                            val payload = credential.payload
                            val roles = payload.getClaim("realm_access")
                                ?.asMap()?.get("roles") as? List<*> ?: emptyList<String>()
                            UserPrincipal(
                                userId = payload.subject ?: "test-user",
                                email = "test@example.com",
                                roles = roles.filterIsInstance<String>().toSet(),
                                accessToken = "test-token"
                            )
                        }
                    }
                }
                routing {
                    adminRoutes(orderService)
                }
            }

            val token = JWT.create()
                .withSubject("test-user")
                .withClaim("email", "test@example.com")
                .withClaim("realm_access", mapOf("roles" to listOf("user")))
                .sign(Algorithm.HMAC256(TEST_SECRET))

            val response = client.get("/admin/orders") {
                header("Authorization", "Bearer $token")
            }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    test("should return 200 OK when user is an admin") {
        val orderService = createTestContext().service

        testApplication {
            application {
                install(ContentNegotiation) { json() }
                install(Authentication) {
                    jwt("user") {
                        verifier(JWT.require(Algorithm.HMAC256(TEST_SECRET)).build())
                        validate { credential ->
                            val payload = credential.payload
                            val roles = payload.getClaim("realm_access")
                                ?.asMap()?.get("roles") as? List<*> ?: emptyList<String>()
                            UserPrincipal(
                                userId = payload.subject ?: "test-admin",
                                email = "admin@example.com",
                                roles = roles.filterIsInstance<String>().toSet(),
                                accessToken = "test-token"
                            )
                        }
                    }
                }
                routing {
                    adminRoutes(orderService)
                }
            }

            val token = JWT.create()
                .withSubject("test-admin")
                .withClaim("email", "admin@example.com")
                .withClaim("realm_access", mapOf("roles" to listOf("admin")))
                .sign(Algorithm.HMAC256(TEST_SECRET))

            val response = client.get("/admin/orders") {
                header("Authorization", "Bearer $token")
            }

            assertEquals(HttpStatusCode.OK, response.status, "Response status should be OK, but was ${response.status}")
        }
    }
}

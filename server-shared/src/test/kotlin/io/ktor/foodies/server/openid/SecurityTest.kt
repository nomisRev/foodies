package io.ktor.foodies.server.openid

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.foodies.server.auth.authTest
import io.ktor.foodies.server.auth.createServiceToken
import io.ktor.foodies.server.auth.createUserToken
import io.ktor.foodies.server.auth.installTestAuth
import io.ktor.foodies.server.auth.secureService
import io.ktor.foodies.server.auth.secureUser
import io.ktor.foodies.server.auth.servicePrincipal
import io.ktor.foodies.server.auth.userPrincipal
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val securitySpec by testSuite {
    authTest("user JWT validation extracts userId from subject") { config ->
        installTestAuth(config)
        application {
            routing {
                secureUser {
                    get("/user") {
                        val principal = userPrincipal()
                        call.respondText(principal.userId)
                    }
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
        installTestAuth(config)
        application {
            routing {
                secureUser {
                    get("/user") {
                        val principal = userPrincipal()
                        call.respondText(principal.email ?: "no-email")
                    }
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
        installTestAuth(config)
        application {
            routing {
                secureUser {
                    get("/user") {
                        val principal = userPrincipal()
                        call.respondText(principal.roles.sorted().joinToString(","))
                    }
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
        installTestAuth(config)
        application {
            routing {
                secureUser {
                    get("/user") {
                        call.respondText("Success")
                    }
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
        installTestAuth(config)
        application {
            routing {
                secureUser {
                    get("/user") {
                        val principal = userPrincipal()
                        call.respondText("Token length: ${principal.accessToken.length}")
                    }
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

    authTest("service JWT validation extracts serviceAccountId from subject") { config ->
        installTestAuth(config)
        application {
            routing {
                secureService {
                    get("/service") {
                        val principal = servicePrincipal()
                        call.respondText(principal.serviceAccountId)
                    }
                }
            }
        }

        val token = createServiceToken(config, serviceAccountId = "service-account-basket-service")
        val response = client.get("/service") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("service-account-basket-service", response.bodyAsText())
    }

    authTest("service JWT validation extracts clientId from azp claim") { config ->
        installTestAuth(config)
        application {
            routing {
                secureService {
                    get("/service") {
                        val principal = servicePrincipal()
                        call.respondText(principal.clientId)
                    }
                }
            }
        }

        val token = createServiceToken(config, clientId = "basket-service")
        val response = client.get("/service") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("basket-service", response.bodyAsText())
    }

    authTest("service JWT validation extracts clientId from client_id claim as fallback") { config ->
        installTestAuth(config)
        application {
            routing {
                secureService {
                    get("/service") {
                        val principal = servicePrincipal()
                        call.respondText(principal.clientId)
                    }
                }
            }
        }

        val token = com.auth0.jwt.JWT.create()
            .withSubject("service-account-payment-service")
            .withClaim("client_id", "payment-service")
            .withClaim("realm_access", mapOf("roles" to listOf("service:order:read")))
            .withAudience(config.audience)
            .withIssuer(config.issuer)
            .withExpiresAt(java.util.Date(System.currentTimeMillis() + 3600000))
            .sign(config.algorithm)

        val response = client.get("/service") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("payment-service", response.bodyAsText())
    }

    authTest("service JWT validation requires clientId ending with -service") { config ->
        installTestAuth(config)
        application {
            routing {
                secureService {
                    get("/service") {
                        call.respondText("Success")
                    }
                }
            }
        }

        val invalidServiceToken = com.auth0.jwt.JWT.create()
            .withSubject("invalid-service-account")
            .withClaim("azp", "not-a-service-client")
            .withClaim("realm_access", mapOf("roles" to listOf("service:read")))
            .withAudience(config.audience)
            .withIssuer(config.issuer)
            .withExpiresAt(java.util.Date(System.currentTimeMillis() + 3600000))
            .sign(config.algorithm)

        val response = client.get("/service") {
            header(HttpHeaders.Authorization, "Bearer $invalidServiceToken")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    authTest("service JWT validation extracts roles from realm_access") { config ->
        installTestAuth(config)
        application {
            routing {
                secureService {
                    get("/service") {
                        val principal = servicePrincipal()
                        call.respondText(principal.roles.sorted().joinToString(","))
                    }
                }
            }
        }

        val token = createServiceToken(
            config,
            roles = listOf("service:basket:read", "service:basket:write", "service:menu:read")
        )
        val response = client.get("/service") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("service:basket:read,service:basket:write,service:menu:read", response.bodyAsText())
    }

    authTest("service JWT validation handles missing realm_access gracefully") { config ->
        installTestAuth(config)
        application {
            routing {
                secureService {
                    get("/service") {
                        val principal = servicePrincipal()
                        call.respondText("Roles: ${principal.roles.size}")
                    }
                }
            }
        }

        val tokenWithoutRoles = com.auth0.jwt.JWT.create()
            .withSubject("service-account-minimal-service")
            .withClaim("azp", "minimal-service")
            .withAudience(config.audience)
            .withIssuer(config.issuer)
            .withExpiresAt(java.util.Date(System.currentTimeMillis() + 3600000))
            .sign(config.algorithm)

        val response = client.get("/service") {
            header(HttpHeaders.Authorization, "Bearer $tokenWithoutRoles")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Roles: 0", response.bodyAsText())
    }
}

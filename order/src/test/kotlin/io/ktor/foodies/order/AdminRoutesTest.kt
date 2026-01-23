package io.ktor.foodies.order

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.foodies.server.test.createUserToken
import io.ktor.foodies.server.test.installTestAuth
import io.ktor.foodies.server.test.testApplication
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlin.test.assertEquals

val adminRoutesSpec by testSuite {
    testApplication("should return 403 Forbidden when user is not an admin") {
        val orderService = createTestContext().service
        installTestAuth()
        routing { adminRoutes(orderService) }

        val token = createUserToken()

        val response = client.get("/admin/orders") {
            header("Authorization", "Bearer $token")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    testApplication("should return 200 OK when user is an admin") {
        val orderService = createTestContext().service
        installTestAuth()
        install(ContentNegotiation) { json() }
        routing {
            adminRoutes(orderService)
        }

        val token = createUserToken(roles = listOf("admin"))

        val response = client.get("/admin/orders") {
            header("Authorization", "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status, "Response status should be OK, but was ${response.status}")
    }
}

package io.ktor.foodies.server

import io.ktor.app.io.ktor.foodies.server.app
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthTest {
    @Test
    fun ok() = testApplication {
        application { app() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}

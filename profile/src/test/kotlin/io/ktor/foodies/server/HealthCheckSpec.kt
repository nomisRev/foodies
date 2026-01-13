package io.ktor.foodies.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.foodies.server.test.ctxSuite
import io.ktor.http.HttpStatusCode
import kotlin.test.assertEquals
import kotlin.test.assertContains

val healthCheckSpec by ctxSuite(context = { serviceContext() }) {

    testProfileService("liveness probe returns 200 OK") {
        val response = client.get("/healthz/liveness")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    testProfileService("readiness probe checks database connectivity") {
        val response = client.get("/healthz/readiness")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()
        assertContains(body, "hikari_open_connections", ignoreCase = true)
    }

    testProfileService("readiness probe checks database connectivity") {
        val response = client.get("/healthz/readiness")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()
        assertContains(body, "rabbitmq", ignoreCase = true)
    }
}

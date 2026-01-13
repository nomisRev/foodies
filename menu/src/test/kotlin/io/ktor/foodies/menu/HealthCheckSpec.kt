package io.ktor.foodies.menu

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.foodies.server.test.ctxSuite
import io.ktor.http.HttpStatusCode
import kotlin.test.assertEquals
import kotlin.test.assertContains

val healthCheckSpec by ctxSuite(context = { serviceContext() }) {
    testMenuService("startup probe returns 200 OK") {
        val response = client.get("/healthz/startup")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    testMenuService("liveness probe returns 200 OK") {
        val response = client.get("/healthz/liveness")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    testMenuService("readiness probe returns 200 OK when database is healthy") {
        val response = client.get("/healthz/readiness")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()
        assertContains(body, "hikari_open_connections", ignoreCase = true)
        assertContains(body, "healthy", ignoreCase = true)
    }

    testMenuService("readiness probe checks database connectivity") {
        val healthyResponse = client.get("/healthz/readiness")
        assertEquals(HttpStatusCode.OK, healthyResponse.status)

        val body = healthyResponse.bodyAsText()
        assertContains(body, "hikari_open_connections", ignoreCase = true)
    }
}

package io.ktor.foodies.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.foodies.server.test.ctxSuite
import io.ktor.foodies.server.test.eventually
import io.ktor.http.HttpStatusCode
import kotlin.test.assertEquals
import kotlin.test.assertContains

/*
val healthCheckSpec by ctxSuite(context = { serviceContext() }) {

    testWebAppService("startup probe returns 200 OK") {
        eventually {
            val response = client.get("/healthz/startup")
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    testWebAppService("liveness probe returns 200 OK") {
        eventually {
            val response = client.get("/healthz/liveness")
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    testWebAppService("readiness probe returns 503 when menu service is unavailable") {
        val response = client.get("/healthz/readiness")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)

        val body = response.bodyAsText()
        assertContains(body, "menu-service", ignoreCase = true)
        assertContains(body, "unhealthy", ignoreCase = true)
    }

    testWebAppService("readiness probe returns 503 when basket service is unavailable") {
        val response = client.get("/healthz/readiness")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)

        val body = response.bodyAsText()
        assertContains(body, "basket-service", ignoreCase = true)
        assertContains(body, "unhealthy", ignoreCase = true)
    }

    testWebAppService("readiness probe checks redis connectivity") {
        val response = client.get("/healthz/readiness")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)

        val body = response.bodyAsText()
        assertContains(body, "redis", ignoreCase = true)
    }
}
*/

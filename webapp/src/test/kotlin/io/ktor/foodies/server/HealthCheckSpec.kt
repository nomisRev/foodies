package io.ktor.foodies.server

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.foodies.server.test.ctxSuite
import io.ktor.http.HttpStatusCode
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.time.Duration.Companion.minutes

val healthCheckSpec by ctxSuite(
    context = { serviceContext() },
    testConfig = TestConfig.testScope(true, timeout = 2.minutes) // cold CI start to pull & start keycloak
) {

    testWebAppService("startup probe returns 200 OK") {
        startApplication()
        val response = client.get("/healthz/startup")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    testWebAppService("liveness probe returns 200 OK") {
        startApplication()
        val response = client.get("/healthz/liveness")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    testWebAppService("readiness probe checks redis connectivity") {
        startApplication()
        val response = client.get("/healthz/readiness")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)

        val body = response.bodyAsText()
        assertContains(body, "redis", ignoreCase = true)
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
}

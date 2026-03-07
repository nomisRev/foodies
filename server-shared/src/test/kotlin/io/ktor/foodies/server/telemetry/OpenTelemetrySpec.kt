package io.ktor.foodies.server.telemetry

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.server.test.testApplication
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val openTelemetrySpec by testSuite {
    testApplication("openTelemetry configures metrics endpoint") {
        application {
            openTelemetry("http://localhost:4317")
        }

        val response = client.get("/metrics")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().isNotBlank())
    }
}

package io.ktor.foodies.server

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.test.assertEquals

val healthSpec by testSuite {
    testApplication("health endpoint returns 200 OK") {
        routing { healthz() }
        val response = client.get("/healthz")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}

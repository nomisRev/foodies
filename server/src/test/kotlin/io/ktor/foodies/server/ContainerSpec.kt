package io.ktor.foodies.server

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.app.io.ktor.foodies.server.app
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.test.assertEquals

val containerSpec by testSuite {
    val module = testModule()

    testApplication("app") {
        application { app(module()) }
        val response = client.get("/healthz")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}

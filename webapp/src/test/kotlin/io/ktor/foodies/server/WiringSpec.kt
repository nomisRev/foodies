package io.ktor.foodies.server

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.foodies.server.test.ctxSuite
import io.ktor.http.HttpStatusCode
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

val wiringSpec by ctxSuite(
    context = { serviceContext() },
    testConfig = TestConfig.testScope(true, timeout = 2.minutes)
) {
    testWebAppService("home route returns 200 and renders title") {
        startApplication()
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "Foodies - Discover the menu")
    }

    testWebAppService("home stylesheet is served from static resources") {
        startApplication()
        val response = client.get("/static/home.css")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), ".logo")
    }

    testWebAppService("cart badge route returns htmx fragment") {
        startApplication()
        val response = client.get("/cart/badge")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "id=\"cart-badge\"")
    }
}

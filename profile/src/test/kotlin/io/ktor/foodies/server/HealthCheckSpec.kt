package io.ktor.foodies.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.foodies.server.test.ctxSuite
import io.ktor.foodies.server.test.eventually
import io.ktor.http.HttpStatusCode
import kotlin.test.assertContains
import kotlin.test.assertEquals

val healthCheckSpec by
    ctxSuite(context = { serviceContext() }) {
        testProfileService("liveness probe returns 200 OK") {
            eventually {
                val response = client.get("/healthz/liveness")
                assertEquals(HttpStatusCode.OK, response.status)
            }
        }

        testProfileService("readiness probe checks database connectivity") {
            val response = client.get("/healthz/readiness")

            eventually {
                assertEquals(HttpStatusCode.OK, response.status)
                val body = response.bodyAsText()
                assertContains(body, "hikari_open_connections", ignoreCase = true)
            }
        }

        testProfileService("readiness probe checks database connectivity") {
            val response = client.get("/healthz/readiness")

            eventually {
                assertEquals(HttpStatusCode.OK, response.status)
                val body = response.bodyAsText()
                assertContains(body, "rabbitmq", ignoreCase = true)
            }
        }
    }

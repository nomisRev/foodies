package io.ktor.foodies.order.placement

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.foodies.server.test.testApplication
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.request.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private fun basketPayload(buyerId: String) = """
{
  "buyerId": "$buyerId",
  "items": [
    {
      "menuItemId": 1,
      "menuItemName": "Burger",
      "menuItemImageUrl": "image-url",
      "unitPrice": "10.00",
      "quantity": 2
    }
  ]
}
"""

val httpBasketClientSpec by testSuite {
    testApplication("forwards token argument as Authorization header") {
        var authorizationHeader: String? = null

        application {
            routing {
                get("/basket") {
                    authorizationHeader = call.request.header(HttpHeaders.Authorization)
                    call.respondText(
                        basketPayload("buyer-from-call"),
                        status = HttpStatusCode.OK,
                        contentType = ContentType.Application.Json
                    )
                }
            }
        }

        val httpClient = createClient {
            install(ContentNegotiation) { json() }
        }
        val basketClient = HttpBasketClient(httpClient, "")

        val basket = basketClient.getBasket(
            buyerId = "buyer-from-call",
            token = "token-from-call"
        )

        assertEquals("Bearer token-from-call", authorizationHeader)
        assertEquals("buyer-from-call", assertNotNull(basket).buyerId)
    }

    testApplication("validates buyerId argument against basket payload") {
        application {
            routing {
                get("/basket") {
                    call.respondText(
                        basketPayload("buyer-from-service"),
                        status = HttpStatusCode.OK,
                        contentType = ContentType.Application.Json
                    )
                }
            }
        }

        val httpClient = createClient {
            install(ContentNegotiation) { json() }
        }
        val basketClient = HttpBasketClient(httpClient, "")

        assertFailsWith<IllegalStateException> {
            basketClient.getBasket(
                buyerId = "buyer-from-call",
                token = "token-from-call"
            )
        }
    }

    testApplication("returns null when basket endpoint returns 404") {
        application {
            routing {
                get("/basket") {
                    call.respondText("not found", status = HttpStatusCode.NotFound)
                }
            }
        }

        val httpClient = createClient {
            install(ContentNegotiation) { json() }
        }
        val basketClient = HttpBasketClient(httpClient, "")

        val basket = basketClient.getBasket(
            buyerId = "buyer-from-call",
            token = "token-from-call"
        )

        assertNull(basket)
    }
}

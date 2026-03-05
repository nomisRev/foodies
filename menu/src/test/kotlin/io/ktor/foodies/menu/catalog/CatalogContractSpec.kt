package io.ktor.foodies.menu.catalog

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.foodies.menu.admin.CreateMenuItemRequest
import io.ktor.foodies.menu.MenuItemResponse
import io.ktor.foodies.menu.serviceContext
import io.ktor.foodies.menu.testMenuService
import io.ktor.foodies.server.test.ctxSuite
import io.ktor.foodies.server.test.jsonClient
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.math.BigDecimal
import kotlin.test.assertContains
import kotlin.test.assertEquals

val catalogContractSpec by ctxSuite(context = { serviceContext() }) {
    val pizza = CreateMenuItemRequest(
        name = "Margherita",
        description = "Classic tomato and mozzarella",
        imageUrl = "https://example.com/margherita.jpg",
        price = BigDecimal("8.50"),
        stock = 15,
    )

    testMenuService("GET /menu returns all items") {
        jsonClient().post("/menu") {
            contentType(ContentType.Application.Json)
            setBody(pizza)
        }
        jsonClient().post("/menu") {
            contentType(ContentType.Application.Json)
            setBody(pizza.copy(name = "Pepperoni"))
        }

        val response = jsonClient().get("/menu?limit=50")

        assertEquals(HttpStatusCode.OK, response.status)
        val items = response.body<List<MenuItemResponse>>()
        assertContains(items.map { it.name }, "Margherita")
        assertContains(items.map { it.name }, "Pepperoni")
    }

    testMenuService("GET /menu respects limit parameter") {
        repeat(5) { i ->
            jsonClient().post("/menu") {
                contentType(ContentType.Application.Json)
                setBody(pizza.copy(name = "Item $i"))
            }
        }

        val response = jsonClient().get("/menu?limit=2")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2, response.body<List<MenuItemResponse>>().size)
    }

    testMenuService("GET /menu/{id} returns item by id") {
        val created = jsonClient().post("/menu") {
            contentType(ContentType.Application.Json)
            setBody(pizza)
        }.body<MenuItemResponse>()

        val response = jsonClient().get("/menu/${created.id}")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(created, response.body())
    }

    testMenuService("GET /menu/{id} returns 404 for missing item") {
        val response = jsonClient().get("/menu/-100")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    testMenuService("GET /menu/{id} returns 400 for invalid id") {
        val response = jsonClient().get("/menu/not-an-id")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}

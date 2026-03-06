package io.ktor.foodies.menu.admin

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.foodies.menu.MenuItemResponse
import io.ktor.foodies.menu.serviceContext
import io.ktor.foodies.menu.testMenuService
import io.ktor.foodies.server.test.ctxSuite
import io.ktor.foodies.server.test.jsonClient
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

val adminContractSpec by ctxSuite(context = { serviceContext() }) {
    val pizza = CreateMenuItemRequest(
        name = "Margherita",
        description = "Classic tomato and mozzarella",
        imageUrl = "https://example.com/margherita.jpg",
        price = BigDecimal("8.50"),
        stock = 15,
    )

    testMenuService("POST /menu creates item and returns 201") {
        val response = jsonClient().post("/menu") {
            contentType(ContentType.Application.Json)
            setBody(pizza)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val item = response.body<MenuItemResponse>()
        assertEquals("Margherita", item.name)
        assertEquals(15, item.stock)
    }

    testMenuService("POST /menu returns 400 for invalid payload") {
        val response = jsonClient().post("/menu") {
            contentType(ContentType.Application.Json)
            setBody(pizza.copy(name = " ", price = BigDecimal.ZERO, stock = -1))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    testMenuService("PUT /menu/{id} updates existing item") {
        val created = jsonClient().post("/menu") {
            contentType(ContentType.Application.Json)
            setBody(pizza)
        }.body<MenuItemResponse>()

        val response = jsonClient().put("/menu/${created.id}") {
            contentType(ContentType.Application.Json)
            setBody(UpdateMenuItemRequest(name = "Pepperoni", stock = 5))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val updated = response.body<MenuItemResponse>()
        assertEquals("Pepperoni", updated.name)
        assertEquals(5, updated.stock)
    }

    testMenuService("PUT /menu/{id} returns 404 for missing item") {
        val response = jsonClient().put("/menu/-100") {
            contentType(ContentType.Application.Json)
            setBody(UpdateMenuItemRequest(name = "Ghost"))
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    testMenuService("PUT /menu/{id} returns 400 for invalid id") {
        val response = jsonClient().put("/menu/not-an-id") {
            contentType(ContentType.Application.Json)
            setBody(UpdateMenuItemRequest(name = "Ghost"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    testMenuService("DELETE /menu/{id} deletes existing item") {
        val created = jsonClient().post("/menu") {
            contentType(ContentType.Application.Json)
            setBody(pizza)
        }.body<MenuItemResponse>()

        assertNotNull(created.id)

        val response = jsonClient().delete("/menu/${created.id}")

        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    testMenuService("DELETE /menu/{id} returns 404 for missing item") {
        val response = jsonClient().delete("/menu/-100")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    testMenuService("DELETE /menu/{id} returns 400 for invalid id") {
        val response = jsonClient().delete("/menu/not-an-id")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}

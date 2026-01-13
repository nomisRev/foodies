package io.ktor.foodies.menu

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.foodies.server.test.ctxSuite
import io.ktor.foodies.server.test.jsonClient
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import kotlin.test.assertContains
import kotlin.test.assertEquals

val menuSpec by ctxSuite(context = { serviceContext() }) {
    val sushi = CreateMenuItemRequest(
        name = "Sushi Roll",
        description = "Fresh salmon roll",
        imageUrl = "https://example.com/salmon-roll.jpg",
        price = BigDecimal("9.50"),
        stock = 10,
    )
    testMenuService("Create Menu Item") {
        val response = jsonClient().post("/menu") {
            contentType(ContentType.Application.Json)
            setBody(sushi)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val item = response.body<MenuItemResponse>()
        assertEquals(item.name, sushi.name)
        assertEquals(item.description, sushi.description)
        assertEquals(item.imageUrl, sushi.imageUrl)
        assertEquals(item.price, sushi.price)
        assertEquals(item.stock, sushi.stock)
    }

    testMenuService("Get created item") {
        val created = jsonClient().post("/menu") {
            contentType(ContentType.Application.Json)
            setBody(sushi)
        }.body<MenuItemResponse>()
        val response = jsonClient().get("/menu/${created.id}")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(created, response.body())
    }

    testMenuService("Get list with pagination - default parameters") {
        // Create multiple items
        val item1 = jsonClient().post("/menu") {
            contentType(ContentType.Application.Json)
            setBody(sushi.copy(name = "Item 1"))
        }.body<MenuItemResponse>()
        val item2 = jsonClient().post("/menu") {
            contentType(ContentType.Application.Json)
            setBody(sushi.copy(name = "Item 2"))
        }.body<MenuItemResponse>()
        val item3 = jsonClient().post("/menu") {
            contentType(ContentType.Application.Json)
            setBody(sushi.copy(name = "Item 3"))
        }.body<MenuItemResponse>()

        val response = jsonClient().get("/menu")
        assertEquals(HttpStatusCode.OK, response.status)
        val items = response.body<List<MenuItemResponse>>()
        assertContains(items, item1)
        assertContains(items, item2)
        assertContains(items, item3)
    }

    testMenuService("Get list with pagination - limit parameter") {
        jsonClient().post("/menu") {
            contentType(ContentType.Application.Json)
            setBody(sushi.copy(name = "Item 1"))
        }
        jsonClient().post("/menu") {
            contentType(ContentType.Application.Json)
            setBody(sushi.copy(name = "Item 2"))
        }
        jsonClient().post("/menu") {
            contentType(ContentType.Application.Json)
            setBody(sushi.copy(name = "Item 3"))
        }

        val response = jsonClient().get("/menu?limit=2")
        assertEquals(HttpStatusCode.OK, response.status)
        val items = response.body<List<MenuItemResponse>>()
        assertEquals(2, items.size)
    }

    listOf(null, "name").flatMap { nameOrNull ->
        listOf(null, "description").flatMap { descriptionOrNull ->
            listOf(null, "imageUrl").flatMap { imageUrlOrNull ->
                listOf(null, BigDecimal("12.50")).flatMap { priceOrNull ->
                    listOf(null, 20).map { stockOrNull ->
                        UpdateMenuItemRequest(
                            name = nameOrNull,
                            description = descriptionOrNull,
                            imageUrl = imageUrlOrNull,
                            price = priceOrNull,
                            stock = stockOrNull
                        )
                    }
                }
            }
        }
    }.forEach { updateRequest ->
        testMenuService("Update existing menu item with ${Json.encodeToString(updateRequest)}") {
            val created = jsonClient().post("/menu") {
                contentType(ContentType.Application.Json)
                setBody(sushi)
            }.body<MenuItemResponse>()

            val response = jsonClient().put("/menu/${created.id}") {
                contentType(ContentType.Application.Json)
                setBody(updateRequest)
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val updated = response.body<MenuItemResponse>()
            assertEquals(created.id, updated.id)
            assertEquals(updateRequest.name ?: created.name, updated.name)
            assertEquals(updateRequest.description ?: created.description, updated.description)
            assertEquals(updateRequest.imageUrl ?: created.imageUrl, updated.imageUrl)
            assertEquals(updateRequest.price ?: created.price, updated.price)
            assertEquals(updateRequest.stock ?: created.stock, updated.stock)
        }
    }

    testMenuService("Update missing item returns 404") {
        val updateRequest = CreateMenuItemRequest(
            name = "Non-existent Item",
            description = "This should fail",
            imageUrl = "https://example.com/fail.jpg",
            price = BigDecimal("5.00")
        )

        val response = jsonClient().put("/menu/-100") {
            contentType(ContentType.Application.Json)
            setBody(updateRequest)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    testMenuService("Update with incorrect path param") {
        val updateRequest = CreateMenuItemRequest(
            name = "Invalid Update",
            description = "This should fail",
            imageUrl = "https://example.com/fail.jpg",
            price = BigDecimal("5.00")
        )

        val response = jsonClient().put("/menu/not-an-id") {
            contentType(ContentType.Application.Json)
            setBody(updateRequest)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    testMenuService("Delete created item") {
        val created = jsonClient().post("/menu") {
            contentType(ContentType.Application.Json)
            setBody(sushi)
        }.body<MenuItemResponse>()
        val response = jsonClient().delete("/menu/${created.id}")
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    testMenuService("Delete missing item returns 404") {
        val response = jsonClient().delete("/menu/-100")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    testMenuService("Delete incorrect path param") {
        val response = jsonClient().delete("/menu/not-an-id")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}

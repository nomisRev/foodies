package io.ktor.foodies.menu

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.foodies.server.test.ctxSuite
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

    // Existing tests, updated to use authorizedJsonClient(null)
    testMenuService("Create Menu Item (unauthenticated)") {
        val response = authorizedJsonClient(null).post("/menu") {
            contentType(ContentType.Application.Json)
            setBody(sushi)
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    testMenuService("Get created item (unauthenticated)") {
        val response = authorizedJsonClient(null).get("/menu/1") // Use a dummy ID as we expect unauthorized
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    testMenuService("Get list with pagination - default parameters (unauthenticated)") {
        val response = authorizedJsonClient(null).get("/menu?limit=50")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    testMenuService("Get list with pagination - limit parameter (unauthenticated)") {
        val response = authorizedJsonClient(null).get("/menu?limit=2")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    testMenuService("Update existing menu item (unauthenticated)") {
        val updateRequest = UpdateMenuItemRequest(name = "Updated Sushi")
        val response = authorizedJsonClient(null).put("/menu/1") { // Use a dummy ID
            contentType(ContentType.Application.Json)
            setBody(updateRequest)
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    testMenuService("Delete created item (unauthenticated)") {
        val response = authorizedJsonClient(null).delete("/menu/1") // Use a dummy ID
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }


    // New JWT authentication tests
    testMenuService("Access /menu with valid JWT token") {
        val validToken = generateJwtToken(subject = "order-service", audience = "menu-service")
        val response = authorizedJsonClient(validToken).get("/menu")
        assertEquals(HttpStatusCode.OK, response.status)
        // Add more assertions here for the actual content if needed
    }

    testMenuService("Access /menu with missing JWT token") {
        val response = authorizedJsonClient(null).get("/menu")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    testMenuService("Access /menu with invalid JWT token (wrong audience)") {
        val wrongAudienceToken = generateJwtToken(subject = "order-service", audience = "other-service")
        val response = authorizedJsonClient(wrongAudienceToken).get("/menu")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    testMenuService("Access /menu with invalid JWT token (bad signature)") {
        val badSignatureToken = generateJwtToken(subject = "order-service", audience = "menu-service", secret = "wrong-secret")
        val response = authorizedJsonClient(badSignatureToken).get("/menu")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // A complete flow with authentication
    testMenuService("Complete flow with authentication") {
        val validToken = generateJwtToken(subject = "order-service", audience = "menu-service")

        // Create
        val createResponse = authorizedJsonClient(validToken).post("/menu") {
            contentType(ContentType.Application.Json)
            setBody(sushi)
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val createdItem = createResponse.body<MenuItemResponse>()

        // Get single
        val getResponse = authorizedJsonClient(validToken).get("/menu/${createdItem.id}")
        assertEquals(HttpStatusCode.OK, getResponse.status)
        assertEquals(createdItem, getResponse.body())

        // Update
        val updateRequest = UpdateMenuItemRequest(name = "Updated Sushi Roll", stock = 15)
        val updateResponse = authorizedJsonClient(validToken).put("/menu/${createdItem.id}") {
            contentType(ContentType.Application.Json)
            setBody(updateRequest)
        }
        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val updatedItem = updateResponse.body<MenuItemResponse>()
        assertEquals(updateRequest.name, updatedItem.name)
        assertEquals(updateRequest.stock, updatedItem.stock)

        // Delete
        val deleteResponse = authorizedJsonClient(validToken).delete("/menu/${createdItem.id}")
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        // Verify deletion
        val getAfterDeleteResponse = authorizedJsonClient(validToken).get("/menu/${createdItem.id}")
        assertEquals(HttpStatusCode.NotFound, getAfterDeleteResponse.status)
    }
}

package io.ktor.foodies.menu

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.server.ValidationException
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

val menuValidationSpec by testSuite {
    test("CreateMenuItemRequest.validate succeeds for valid data") {
        val request = CreateMenuItemRequest(
            name = "Pizza",
            description = "Delicious cheese pizza",
            imageUrl = "https://example.com/pizza.jpg",
            price = BigDecimal("10.99"),
        )

        assertEquals(
            CreateMenuItem(
                name = "Pizza",
                description = "Delicious cheese pizza",
                imageUrl = "https://example.com/pizza.jpg",
                price = BigDecimal("10.99"),
            ),
            request.validate()
        )
    }

    test("CreateMenuItemRequest.validate fails for blank fields or non-positive price") {
        val error = assertFailsWith<ValidationException> {
            CreateMenuItemRequest(
                name = " ",
                description = "",
                imageUrl = " ",
                price = BigDecimal.ZERO,
            ).validate()
        }

        assertTrue(error.reasons.contains("name must not be blank"))
        assertTrue(error.reasons.contains("description must not be blank"))
        assertTrue(error.reasons.contains("imageUrl must not be blank"))
        assertTrue(error.reasons.contains("price must be greater than 0"))
    }

    test("UpdateMenuItemRequest.validate rejects blank or non-positive updates") {
        val error = assertFailsWith<ValidationException> {
            UpdateMenuItemRequest(
                description = " ",
                price = BigDecimal("-1.00"),
            ).validate()
        }

        assertTrue(error.reasons.contains("description must not be blank"))
        assertTrue(error.reasons.contains("price must be greater than 0"))
    }

    test("UpdateMenuItemRequest.validate succeeds for valid data") {
        val request = UpdateMenuItemRequest(
            name = "Updated Pizza",
            description = "Even more delicious pizza",
            price = BigDecimal("12.99"),
        )

        assertEquals(
            UpdateMenuItem(
                name = "Updated Pizza",
                description = "Even more delicious pizza",
                price = BigDecimal("12.99"),
            ),
            request.validate()
        )
    }
}
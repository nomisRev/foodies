package io.ktor.foodies.basket

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.server.plugins.BadRequestException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

val basketValidationSpec by testSuite {
    testSuite("AddItemRequest validation") {
        test("valid request with positive menuItemId and quantity passes validation") {
            val request = AddItemRequest(menuItemId = 1L, quantity = 5)

            val validated = request.validate()

            assertEquals(1L, validated.menuItemId)
            assertEquals(5, validated.quantity)
        }

        test("menuItemId must be positive") {
            val request = AddItemRequest(menuItemId = 0L, quantity = 1)

            val error = assertFailsWith<BadRequestException> { request.validate() }
            assertEquals("menuItemId must be positive", error.message)
        }

        test("negative menuItemId fails validation") {
            val request = AddItemRequest(menuItemId = -1L, quantity = 1)

            val error = assertFailsWith<BadRequestException> { request.validate() }
            assertEquals("menuItemId must be positive", error.message)
        }

        test("quantity must be at least 1") {
            val request = AddItemRequest(menuItemId = 1L, quantity = 0)

            val error = assertFailsWith<BadRequestException> { request.validate() }
            assertEquals("quantity must be at least 1", error.message)
        }

        test("negative quantity fails validation") {
            val request = AddItemRequest(menuItemId = 1L, quantity = -5)

            val error = assertFailsWith<BadRequestException> { request.validate() }
            assertEquals("quantity must be at least 1", error.message)
        }

        test("multiple validation errors are collected") {
            val request = AddItemRequest(menuItemId = -1L, quantity = 0)

            val error = assertFailsWith<BadRequestException> { request.validate() }
            assertEquals("menuItemId must be positive; quantity must be at least 1", error.message)
        }
    }

    testSuite("UpdateItemQuantityRequest validation") {
        test("valid request with positive quantity passes validation") {
            val request = UpdateItemQuantityRequest(quantity = 10)

            val validated = request.validate()

            assertEquals(10, validated.quantity)
        }

        test("quantity of 1 is valid") {
            val request = UpdateItemQuantityRequest(quantity = 1)

            val validated = request.validate()

            assertEquals(1, validated.quantity)
        }

        test("quantity must be at least 1") {
            val request = UpdateItemQuantityRequest(quantity = 0)

            val error = assertFailsWith<BadRequestException> { request.validate() }
            assertEquals("quantity must be at least 1", error.message)
        }

        test("negative quantity fails validation") {
            val request = UpdateItemQuantityRequest(quantity = -3)

            val error = assertFailsWith<BadRequestException> { request.validate() }
            assertEquals("quantity must be at least 1", error.message)
        }
    }
}

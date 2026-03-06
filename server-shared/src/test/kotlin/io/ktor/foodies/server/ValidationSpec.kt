package io.ktor.foodies.server

import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

val validationSpec by testSuite {
    test("validate returns value when no rules fail") {
        val value = validate {
            42.validate({ it > 0 }) { "number must be positive" }
        }

        assertEquals(42, value)
    }

    test("validate throws with all reasons when multiple rules fail") {
        val exception = assertFailsWith<ValidationException> {
            validate {
                "".validate(String::isNotBlank) { "name must not be blank" }
                (-1).validate({ it > 0 }) { "age must be positive" }
            }
        }

        assertEquals("name must not be blank; age must be positive", exception.message)
        assertEquals(listOf("name must not be blank", "age must be positive"), exception.reasons)
    }

    test("validate uses custom formatter for error messages") {
        val exception = assertFailsWith<ValidationException> {
            validate(errorsToMessage = { "failed with ${it.size} errors" }) {
                "".validate(String::isNotBlank) { "name must not be blank" }
            }
        }

        assertEquals("failed with 1 errors", exception.message)
        assertEquals(listOf("name must not be blank"), exception.reasons)
    }

    test("ValidationException createCopy preserves message and reasons") {
        val original = ValidationException("validation failed", listOf("reason"))

        val copy = original.createCopy()

        assertEquals(original.message, copy.message)
        assertEquals(original.reasons, copy.reasons)
    }
}

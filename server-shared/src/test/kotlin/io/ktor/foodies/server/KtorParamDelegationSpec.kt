package io.ktor.foodies.server

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.http.Parameters
import io.ktor.http.parametersOf
import io.ktor.server.plugins.MissingRequestParameterException
import io.ktor.server.plugins.ParameterConversionException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private class IntQuery(private val params: Parameters) {
    val page: Int by params
}

private class NullableQuery(private val params: Parameters) {
    val page: Int by params
    val q: String? by params
}

val ktorParamDelegationSpec by testSuite {
    test("delegated parameter parsing succeeds for required values") {
        val query = IntQuery(parametersOf("page", "12"))

        assertEquals(12, query.page)
    }

    test("delegated nullable parameter returns null when missing") {
        val query = NullableQuery(parametersOf("page", "1"))

        assertNull(query.q)
    }

    test("delegated required parameter throws when missing") {
        val query = IntQuery(Parameters.Empty)

        val exception = assertFailsWith<MissingRequestParameterException> {
            query.page
        }

        assertEquals("page", exception.parameterName)
    }

    test("delegated parameter throws conversion exception when value is invalid") {
        val query = IntQuery(parametersOf("page", "abc"))

        val exception = assertFailsWith<ParameterConversionException> {
            query.page
        }

        assertEquals("page", exception.parameterName)
    }
}

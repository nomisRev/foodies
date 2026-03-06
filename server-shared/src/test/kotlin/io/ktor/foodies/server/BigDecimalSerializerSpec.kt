package io.ktor.foodies.server

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import kotlin.test.assertEquals

@Serializable
private data class Price(val amount: SerializableBigDecimal)

val bigDecimalSerializerSpec by testSuite {
    test("serializer writes plain decimal string representation") {
        val payload = Price(BigDecimal("1000.50"))

        val json = Json.encodeToString(payload)

        assertEquals("""{"amount":"1000.50"}""", json)
    }

    test("serializer reads decimal value from string") {
        val payload = Json.decodeFromString<Price>("""{"amount":"12.34"}""")

        assertEquals(BigDecimal("12.34"), payload.amount)
    }
}

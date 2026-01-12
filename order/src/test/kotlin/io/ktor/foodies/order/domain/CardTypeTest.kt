package io.ktor.foodies.order.domain

import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals

val cardTypeSpec by testSuite {
    test("should have correct display names") {
        assertEquals("Visa", CardType.Visa.displayName)
        assertEquals("MasterCard", CardType.MasterCard.displayName)
        assertEquals("American Express", CardType.Amex.displayName)
    }

    test("should have correct names") {
        assertEquals("Visa", CardType.Visa.name)
        assertEquals("MasterCard", CardType.MasterCard.name)
        assertEquals("Amex", CardType.Amex.name)
    }
}

package io.ktor.foodies.order.domain

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.events.common.CardBrand
import kotlin.test.assertEquals

val cardBrandSpec by testSuite {
    test("should have correct display names") {
        assertEquals("Visa", CardBrand.VISA.displayName)
        assertEquals("MasterCard", CardBrand.MASTERCARD.displayName)
        assertEquals("American Express", CardBrand.AMEX.displayName)
    }

    test("should have correct names") {
        assertEquals("VISA", CardBrand.VISA.name)
        assertEquals("MASTERCARD", CardBrand.MASTERCARD.name)
        assertEquals("AMEX", CardBrand.AMEX.name)
    }
}

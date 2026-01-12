package io.ktor.foodies.order.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CardTypeTest {
    @Test
    fun `should have correct display names`() {
        assertEquals("Visa", CardType.Visa.displayName)
        assertEquals("MasterCard", CardType.MasterCard.displayName)
        assertEquals("American Express", CardType.Amex.displayName)
    }

    @Test
    fun `should have correct names`() {
        assertEquals("Visa", CardType.Visa.name)
        assertEquals("MasterCard", CardType.MasterCard.name)
        assertEquals("Amex", CardType.Amex.name)
    }
}

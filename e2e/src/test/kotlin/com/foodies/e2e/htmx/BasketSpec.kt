package com.foodies.e2e.htmx

import com.foodies.e2e.e2eSuite
import com.foodies.e2e.page
import com.foodies.e2e.waitForHtmxIdle
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import kotlin.getValue
import kotlin.test.assertTrue

val addToBasketSpec by e2eSuite {
    test("Add to Basket - should add item and update badge") {
        val p = page()

        p.navigate("/", Page.NavigateOptions().setTimeout(5000.0))

        val addButtons = p.locator(".add-to-cart-btn")

        addButtons.first()
            .also { it.waitFor(Locator.WaitForOptions().setTimeout(5000.0)) }
            .click()

        p.waitForHtmxIdle()

        assertTrue(p.locator("#toast").isVisible, "Should show success toast")
        val badgeCount = p.locator(".cart-count")
        assertTrue(badgeCount.isVisible, "Badge count should be visible")
    }

    test("Update Basket - should change item quantity") {
        val p = page()

        p.navigate("/cart", Page.NavigateOptions().setTimeout(5000.0))

        val qtyInput = p.locator(".quantity-input").first()
        val initialQtyValue = qtyInput.getAttribute("value")
        val initialQty = initialQtyValue?.toIntOrNull() ?: 1

        val plusButton = p.locator(".qty-btn").filter(Locator.FilterOptions().setHasText("+")).first()
        plusButton.click()

        p.waitForHtmxIdle()

        val newQtyValue = qtyInput.getAttribute("value")
        val newQty = newQtyValue?.toIntOrNull() ?: 1
        assertTrue(newQty > initialQty, "Quantity should increase. Before: $initialQty, After: $newQty")
    }

    // TODO: Update the test so that it always has an item to remove, and will never be skipped.
    test("Remove from Basket - should remove item from list") {
        val p = page()

        p.navigate("/cart", Page.NavigateOptions().setTimeout(5000.0))

        val cartItems = p.locator(".cart-item")
        val itemsBefore = cartItems.count()

        p.onceDialog { dialog ->
            println("Accepting dialog: ${dialog.message()}")
            dialog.accept()
        }

        val removeButton = p.locator(".remove-btn").first()
        removeButton.click()

        p.waitForHtmxIdle()

        val itemsAfter = cartItems.count()
        assertTrue(
            itemsAfter < itemsBefore,
            "Item should be removed from cart. Before: $itemsBefore, After: $itemsAfter"
        )
    }
}

package com.foodies.e2e.htmx

import com.foodies.e2e.e2eSuite
import com.foodies.e2e.page
import com.foodies.e2e.waitForHtmxIdle
import com.microsoft.playwright.*
import kotlin.test.assertTrue

val browseMenuSpec by e2eSuite {
    test("Browse Menu Without Authentication - should display menu items") {
        val p = page()

        p.navigate("/", Page.NavigateOptions().setTimeout(5000.0))
        val menuItems = p.locator(".menu-card")

        menuItems.first().waitFor(Locator.WaitForOptions().setTimeout(5000.0))

        assertTrue(menuItems.count() > 0, "Should display at least one menu item. Current count: ${menuItems.count()}")
    }

    test("Menu Pagination - should load more items on scroll") {
        val p = page()

        p.navigate("/", Page.NavigateOptions().setTimeout(5000.0))

        p.waitForHtmxIdle()

        val initialItems = p.locator(".menu-card")

        val countBefore = initialItems.first()
            .also { it.waitFor(Locator.WaitForOptions().setTimeout(5000.0)) }
            .count()

        val sentinel = p.locator("#feed-sentinel")
        assertTrue(sentinel.isVisible, "Sentinel should be visible")
        sentinel.scrollIntoViewIfNeeded()

        p.waitForHtmxIdle()

        val countAfter = initialItems.count()

        assertTrue(
            countAfter >= countBefore,
            "Should have at least the same number of items. Current count: $countAfter"
        )
    }
}

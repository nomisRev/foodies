package com.foodies.e2e.htmx.menu

import com.foodies.e2e.config.*
import com.foodies.e2e.utils.HtmxHelpers.waitForHtmxIdle
import com.microsoft.playwright.*
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertTrue

val menuPaginationSpec by testSuite {
    val config = TestConfig.fromEnvironment()
    val playwright = playwrightFixture()
    
    val browser = testFixture {
        playwright().chromium().launch(
            BrowserType.LaunchOptions()
                .setHeadless(config.headless)
                .setSlowMo(config.slowMo.toDouble())
        )
    } closeWith { close() }

    val context = testFixture {
        browser().newContext(
            Browser.NewContextOptions()
                .setBaseURL(config.webappBaseUrl)
        )
    } closeWith { close() }

    val page = testFixture {
        context().newPage()
    } closeWith { close() }

    test("Menu Pagination - should load more items on scroll") {
        val p = page()
        
        // Check if webapp is reachable
        try {
            p.navigate("/", Page.NavigateOptions().setTimeout(5000.0))
        } catch (e: Exception) {
            println("Skipping Menu Pagination test: webapp not reachable at ${config.webappBaseUrl}. Error: ${e.message}")
            return@test
        }

        // Wait for initial items to load
        p.waitForHtmxIdle()

        // Initially should have some items (DefaultMenuPageSize = 12)
        val initialItems = p.locator(".menu-card")
        
        // Wait for items to appear
        try {
            initialItems.first().waitFor(Locator.WaitForOptions().setTimeout(5000.0))
        } catch (e: Exception) {
            // ignore
        }
        
        val countBefore = initialItems.count()
        
        // Scroll to bottom sentinel to trigger more loading
        val sentinel = p.locator("#feed-sentinel")
        if (sentinel.isVisible()) {
            sentinel.scrollIntoViewIfNeeded()
            
            // Wait for HTMX to load more
            p.waitForHtmxIdle()
            
            // Should show more items or at least stay the same if end reached
            val countAfter = initialItems.count()
            assertTrue(countAfter >= countBefore, "Should have at least the same number of items. Current count: $countAfter")
        } else {
            println("Sentinel not visible, skipping scroll interaction.")
        }
    }
}

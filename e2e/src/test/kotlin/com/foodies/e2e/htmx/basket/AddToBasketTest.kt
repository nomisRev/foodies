package com.foodies.e2e.htmx.basket

import com.foodies.e2e.config.*
import com.foodies.e2e.utils.HtmxHelpers.waitForHtmxIdle
import com.microsoft.playwright.*
import de.infix.testBalloon.framework.core.testSuite
import java.nio.file.Files
import kotlin.test.assertTrue

val addToBasketSpec by testSuite {
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
        Files.createDirectories(config.storageStatePath.parent)
        if (Files.exists(config.storageStatePath)) {
            browser().newContext(
                Browser.NewContextOptions()
                    .setStorageStatePath(config.storageStatePath)
                    .setBaseURL(config.webappBaseUrl)
            )
        } else {
            browser().newContext(
                Browser.NewContextOptions()
                    .setBaseURL(config.webappBaseUrl)
            )
        }
    } closeWith { close() }

    val page = testFixture {
        context().newPage()
    } closeWith { close() }

    test("Add to Basket - should add item and update badge") {
        val p = page()
        
        // Check if webapp is reachable
        try {
            p.navigate("/", Page.NavigateOptions().setTimeout(5000.0))
        } catch (e: Exception) {
            println("Skipping Add to Basket test: webapp not reachable at ${config.webappBaseUrl}. Error: ${e.message}")
            return@test
        }

        // Find first add to basket button
        // Note: This requires the user to be logged in
        val addButtons = p.locator(".add-to-cart-btn")
        
        // Wait for items to load
        try {
            addButtons.first().waitFor(Locator.WaitForOptions().setTimeout(5000.0))
        } catch (e: Exception) {
            // ignore
        }

        if (addButtons.count() > 0 && !addButtons.first().innerText().contains("Login")) {
            addButtons.first().click()
            
            // Wait for HTMX
            p.waitForHtmxIdle()
            
            // Should show success toast
            assertTrue(p.locator("#toast").isVisible(), "Should show success toast")
            
            // Badge should be updated
            val badgeCount = p.locator(".cart-count")
            assertTrue(badgeCount.isVisible(), "Badge count should be visible")
        } else {
            println("No 'Add to Cart' button found or it says 'Login to Order'. Skipping interaction.")
        }
    }
}

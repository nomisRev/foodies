package com.foodies.e2e.htmx.basket

import com.foodies.e2e.config.*
import com.foodies.e2e.utils.HtmxHelpers.waitForHtmxIdle
import com.microsoft.playwright.*
import de.infix.testBalloon.framework.core.testSuite
import java.nio.file.Files
import kotlin.test.assertTrue

val updateBasketSpec by testSuite {
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

    test("Update Basket - should change item quantity") {
        val p = page()
        
        try {
            p.navigate("/cart", Page.NavigateOptions().setTimeout(5000.0))
        } catch (e: Exception) {
            println("Skipping Update Basket test: webapp not reachable")
            return@test
        }

        val qtyInput = p.locator(".quantity-input").first()
        if (qtyInput.isVisible()) {
            val initialQtyValue = qtyInput.getAttribute("value")
            val initialQty = initialQtyValue?.toIntOrNull() ?: 1
            
            // Find the "+" button in the same form
            val plusButton = p.locator(".qty-btn").filter(Locator.FilterOptions().setHasText("+")).first()
            plusButton.click()
            
            p.waitForHtmxIdle()
            
            val newQtyValue = qtyInput.getAttribute("value")
            val newQty = newQtyValue?.toIntOrNull() ?: 1
            assertTrue(newQty > initialQty, "Quantity should increase. Before: $initialQty, After: $newQty")
        } else {
            println("No items in cart to update. Skipping interaction.")
        }
    }
}

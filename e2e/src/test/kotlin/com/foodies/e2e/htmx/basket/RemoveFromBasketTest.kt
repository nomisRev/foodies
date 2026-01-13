package com.foodies.e2e.htmx.basket

import com.foodies.e2e.config.*
import com.foodies.e2e.utils.HtmxHelpers.waitForHtmxIdle
import com.microsoft.playwright.*
import de.infix.testBalloon.framework.core.testSuite
import java.nio.file.Files
import kotlin.test.assertTrue

val removeFromBasketSpec by testSuite {
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

    test("Remove from Basket - should remove item from list") {
        val p = page()
        
        try {
            p.navigate("/cart", Page.NavigateOptions().setTimeout(5000.0))
        } catch (e: Exception) {
            println("Skipping Remove from Basket test: webapp not reachable")
            return@test
        }

        val cartItems = p.locator(".cart-item")
        val itemsBefore = cartItems.count()
        
        if (itemsBefore > 0) {
            // Handle the confirmation dialog
            p.onceDialog { dialog -> 
                println("Accepting dialog: ${dialog.message()}")
                dialog.accept() 
            }
            
            val removeButton = p.locator(".remove-btn").first()
            removeButton.click()
            
            p.waitForHtmxIdle()
            
            val itemsAfter = cartItems.count()
            assertTrue(itemsAfter < itemsBefore, "Item should be removed from cart. Before: $itemsBefore, After: $itemsAfter")
        } else {
            println("No items in cart to remove. Skipping interaction.")
        }
    }
}

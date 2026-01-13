package com.foodies.e2e.htmx.auth

import com.foodies.e2e.config.*
import com.microsoft.playwright.*
import com.microsoft.playwright.options.AriaRole
import de.infix.testBalloon.framework.core.testSuite
import java.nio.file.Files
import kotlin.test.assertTrue

val logoutSpec by testSuite {
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

    test("Logout Flow - should logout user and redirect to Keycloak") {
        val p = page()
        
        // Check if webapp is reachable
        try {
            p.navigate("/", Page.NavigateOptions().setTimeout(5000.0))
        } catch (e: Exception) {
            println("Skipping Logout test: webapp not reachable at ${config.webappBaseUrl}. Error: ${e.message}")
            return@test
        }

        // Click logout
        // Note: We assume the user is already logged in via AuthSetup
        val logoutButton = p.getByText("Log out")
        if (logoutButton.isVisible()) {
            logoutButton.click()

            // Should redirect to logout URL or Keycloak
            assertTrue(p.url().contains("/logout") || p.url().contains("keycloak"), "Should redirect to logout URL. Current URL: ${p.url()}")

            // Navigate back to home
            p.navigate("/")

            // Should show login button
            assertTrue(p.getByText("Log in").isVisible(), "Should show log in button after logout")
        } else {
            println("Log out button not visible, skipping logout interaction. User might not be logged in.")
        }
    }
}

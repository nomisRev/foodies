package com.foodies.e2e.setup

import com.foodies.e2e.config.*
import com.microsoft.playwright.*
import com.microsoft.playwright.options.AriaRole
import de.infix.testBalloon.framework.core.testSuite
import java.nio.file.Files
import kotlin.test.assertTrue

/**
 * Authentication setup that runs before authenticated tests.
 * Creates and saves authentication state for reuse across tests.
 */
val authSetupSpec by testSuite {
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

    test("Keycloak Authentication - should authenticate user and save session state") {
        val p = page()
        
        // Check if webapp is reachable
        try {
            p.navigate("/", Page.NavigateOptions().setTimeout(5000.0))
        } catch (e: Exception) {
            println("Skipping Keycloak Authentication test: webapp not reachable at ${config.webappBaseUrl}. Error: ${e.message}")
            return@test
        }

        // Click login button
        p.getByLabel("Sign in").click()

        // Should redirect to Keycloak
        assertTrue(p.url().contains("keycloak"), "Should redirect to Keycloak")

        // Fill login form
        p.getByLabel("Username").fill(config.testUsername)
        p.getByLabel("Password").fill(config.testPassword)
        p.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Sign In")).click()

        // Should redirect back to home page
        p.waitForURL("**/")
        assertTrue(p.getByText("Welcome back").isVisible(), "Should show welcome message")

        // Save authentication state
        Files.createDirectories(config.storageStatePath.parent)
        context().storageState(
            BrowserContext.StorageStateOptions()
                .setPath(config.storageStatePath)
        )
    }
}

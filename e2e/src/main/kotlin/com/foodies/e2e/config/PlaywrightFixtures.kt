package com.foodies.e2e.config

import com.microsoft.playwright.*
import de.infix.testBalloon.framework.core.TestSuite
import java.nio.file.Files

/**
 * Browser type for Playwright.
 */
enum class AppBrowserType {
    CHROMIUM, FIREFOX, WEBKIT
}

/**
 * Creates a Playwright instance fixture that will be properly closed after tests.
 */
fun TestSuite.playwrightFixture(): TestSuite.Fixture<Playwright> =
    testFixture { Playwright.create() } closeWith { close() }

/**
 * Creates a Browser instance fixture.
 */
context(suite: TestSuite)
fun Playwright.browserFixture(
    browserType: AppBrowserType = AppBrowserType.CHROMIUM,
    config: TestConfig
): TestSuite.Fixture<Browser> = suite.testFixture {
    val browserTypeInstance = when (browserType) {
        AppBrowserType.CHROMIUM -> chromium()
        AppBrowserType.FIREFOX -> firefox()
        AppBrowserType.WEBKIT -> webkit()
    }

    browserTypeInstance.launch(
        BrowserType.LaunchOptions()
            .setHeadless(config.headless)
            .setSlowMo(config.slowMo.toDouble())
    )
} closeWith { close() }

/**
 * Creates an authenticated BrowserContext fixture.
 * Will reuse existing authentication state if available.
 */
context(suite: TestSuite)
fun Browser.authenticatedContextFixture(config: TestConfig): TestSuite.Fixture<BrowserContext> =
    suite.testFixture {
        // Ensure storage state directory exists
        Files.createDirectories(config.storageStatePath.parent)

        if (Files.exists(config.storageStatePath)) {
            newContext(
                Browser.NewContextOptions()
                    .setStorageStatePath(config.storageStatePath)
                    .setBaseURL(config.webappBaseUrl)
            )
        } else {
            newContext(
                Browser.NewContextOptions()
                    .setBaseURL(config.webappBaseUrl)
            )
        }
    } closeWith { close() }

/**
 * Creates an unauthenticated BrowserContext fixture.
 */
context(suite: TestSuite)
fun Browser.unauthenticatedContextFixture(config: TestConfig): TestSuite.Fixture<BrowserContext> =
    suite.testFixture {
        newContext(
            Browser.NewContextOptions()
                .setBaseURL(config.webappBaseUrl)
        )
    } closeWith { close() }

/**
 * Creates a Page fixture from a BrowserContext.
 */
context(suite: TestSuite)
fun BrowserContext.pageFixture(): TestSuite.Fixture<Page> =
    suite.testFixture { newPage() } closeWith { close() }

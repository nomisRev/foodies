package com.foodies.e2e

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.framework.shared.TestDisplayName
import de.infix.testBalloon.framework.shared.TestElementName
import de.infix.testBalloon.framework.shared.TestRegistering
import java.nio.file.Files

data class E2EContext(
    val config: E2EConfig,
    val playwright: TestSuite.Fixture<Playwright>,
    val browser: TestSuite.Fixture<Browser>,
    val context: TestSuite.Fixture<BrowserContext>,
    val page: TestSuite.Fixture<Page>,
)

context(ctx: E2EContext)
suspend fun page() = ctx.page.invoke()

context(ctx: E2EContext)
suspend fun context() = ctx.context.invoke()

context(ctx: E2EContext)
val config: E2EConfig
    get() = ctx.config

enum class AppBrowserType { CHROMIUM, FIREFOX, WEBKIT }

@TestRegistering
fun e2eSuite(
    @TestElementName name: String = "",
    @TestDisplayName displayName: String = name,
    testConfig: TestConfig = TestConfig,
    browserType: AppBrowserType = AppBrowserType.CHROMIUM,
    authenticated: Boolean = true,
    content: context(E2EContext) TestSuite.() -> Unit
): Lazy<TestSuite> = testSuite(name, displayName, testConfig) {
    val config = E2EConfig.fromEnvironment()
    val playwright = testFixture { Playwright.create() }
    val browser = testFixture {
        when (browserType) {
            AppBrowserType.CHROMIUM -> playwright().chromium()
            AppBrowserType.FIREFOX -> playwright().firefox()
            AppBrowserType.WEBKIT -> playwright().webkit()
        }.launch(BrowserType.LaunchOptions().setHeadless(config.headless).setSlowMo(config.slowMo.toDouble()))
    }
    val context = testFixture {
        Files.createDirectories(config.storageStatePath.parent)
        if (Files.exists(config.storageStatePath) && authenticated) {
            browser().newContext(
                Browser.NewContextOptions()
                    .setStorageStatePath(config.storageStatePath)
                    .setBaseURL(config.webappBaseUrl)
            )
        } else {
            browser().newContext(Browser.NewContextOptions().setBaseURL(config.webappBaseUrl))
        }
    }

    val page = testFixture { context().newPage() }

    content(E2EContext(config, playwright, browser, context, page), this)
}

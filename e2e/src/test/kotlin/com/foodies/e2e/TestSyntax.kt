package com.foodies.e2e

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.AriaRole
import com.microsoft.playwright.options.WaitUntilState
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.TestFixture
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.framework.shared.TestDisplayName
import de.infix.testBalloon.framework.shared.TestElementName
import de.infix.testBalloon.framework.shared.TestRegistering
import java.nio.file.Files
import java.nio.file.Path

data class E2EContext(
    val config: E2EConfig,
    val playwright: TestFixture<Playwright>,
    val browser: TestFixture<Browser>,
    val context: TestFixture<BrowserContext>,
    val page: TestFixture<Page>,
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
        if (authenticated) {
            browser().newAuthenticatedContext(config, authStorageStatePath(config, name))
        } else {
            browser().newContext(Browser.NewContextOptions().setBaseURL(config.webappBaseUrl))
        }
    }

    val page = testFixture { context().newPage() }

    content(E2EContext(config, playwright, browser, context, page), this)
}

private fun Browser.newAuthenticatedContext(config: E2EConfig, storageStatePath: Path): BrowserContext {
    Files.createDirectories(storageStatePath.parent)
    Files.deleteIfExists(storageStatePath)

    newContext(Browser.NewContextOptions().setBaseURL(config.webappBaseUrl)).use { setupContext ->
        val setupPage = setupContext.newPage()
        setupPage.navigate(
            "/",
            Page.NavigateOptions()
                .setTimeout(config.navigationTimeout.toDouble())
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
        )
        setupPage.getByText("Log in").click()
        setupPage.getByLabel("Username").fill(config.testUsername)
        setupPage.getByLabel("Password", Page.GetByLabelOptions().setExact(true)).fill(config.testPassword)
        setupPage.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Sign In")).click()
        setupPage.waitForURL("/")
        setupPage.getByText("Log out").waitFor()
        setupContext.storageState(BrowserContext.StorageStateOptions().setPath(storageStatePath))
    }

    return newContext(
        Browser.NewContextOptions()
            .setStorageStatePath(storageStatePath)
            .setBaseURL(config.webappBaseUrl)
    )
}

private fun authStorageStatePath(config: E2EConfig, suiteName: String): Path {
    val fileName = suiteName
        .ifBlank { "authenticated-suite" }
        .replace(Regex("[^A-Za-z0-9._-]"), "-")
    return config.storageStatePath.parent.resolve("$fileName.json")
}

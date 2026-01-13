package com.foodies.e2e.config

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import arrow.fx.coroutines.Resource

object PlaywrightFixtures {
    fun playwright(): Resource<Playwright> = Resource({ Playwright.create() }, { p, _ -> p.close() })

    fun browser(playwright: Playwright, config: TestConfig): Resource<Browser> = Resource(
        {
            playwright.chromium().launch(
                BrowserType.LaunchOptions()
                    .setHeadless(config.headless)
                    .setSlowMo(config.slowMo)
            )
        },
        { b, _ -> b.close() }
    )

    fun context(browser: Browser): Resource<BrowserContext> = Resource(
        { browser.newContext() },
        { c, _ -> c.close() }
    )

    fun page(context: BrowserContext): Resource<Page> = Resource(
        { context.newPage() },
        { p, _ -> p.close() }
    )
}

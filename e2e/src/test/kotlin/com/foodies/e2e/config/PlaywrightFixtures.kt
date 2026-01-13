package com.foodies.e2e.config

import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import arrow.fx.coroutines.resourceScope

object PlaywrightFixtures {
    suspend fun <T> withPage(block: suspend (Page) -> T): T = resourceScope {
        val playwright = install({ Playwright.create() }) { p, _ -> p.close() }
        val browser = install({ playwright.chromium().launch() }) { b, _ -> b.close() }
        val context = install({ browser.newContext() }) { c, _ -> c.close() }
        val page = context.newPage()
        block(page)
    }
}

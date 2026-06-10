package com.foodies.e2e

import com.microsoft.playwright.ElementHandle
import com.microsoft.playwright.Page
import com.microsoft.playwright.Response
import com.microsoft.playwright.options.WaitForSelectorState
import com.microsoft.playwright.options.WaitUntilState

context(ctx: E2EContext)
fun Page.navigateApp(path: String): Response? =
    navigate(
        path,
        Page.NavigateOptions()
            .setTimeout(config.navigationTimeout.toDouble())
            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
    )

fun Page.waitForHtmxIdle(): ElementHandle? =
    waitForSelector(".htmx-request", Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN))

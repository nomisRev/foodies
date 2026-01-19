package com.foodies.e2e

import com.microsoft.playwright.ElementHandle
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.WaitForSelectorState

fun Page.waitForHtmxIdle(): ElementHandle? =
    waitForSelector(
        ".htmx-request",
        Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN),
    )

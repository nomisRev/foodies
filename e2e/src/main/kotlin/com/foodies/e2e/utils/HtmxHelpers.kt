package com.foodies.e2e.utils

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.WaitForSelectorState

object HtmxHelpers {
    /**
     * Waits for all HTMX requests to complete by checking for the absence of .htmx-request class.
     */
    fun Page.waitForHtmxIdle() {
        this.waitForSelector(".htmx-request", Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN))
    }
}

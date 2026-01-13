package com.foodies.e2e.utils

import com.microsoft.playwright.Page
import java.nio.file.Paths

object TestHelpers {
    /**
     * Takes a screenshot and saves it to the build/screenshots directory.
     */
    fun Page.takeScreenshot(name: String) {
        this.screenshot(Page.ScreenshotOptions().setPath(Paths.get("build/screenshots/$name.png")))
    }

    /**
     * Scrolls to the bottom of the page.
     */
    fun Page.scrollToBottom() {
        this.evaluate("window.scrollTo(0, document.body.scrollHeight)")
    }
}

package com.foodies.e2e.utils

import com.microsoft.playwright.APIRequest
import com.microsoft.playwright.APIRequestContext
import com.microsoft.playwright.Playwright
import com.foodies.e2e.config.TestConfig

object ApiHelpers {
    /**
     * Creates an APIRequestContext for making direct backend API calls.
     */
    fun createApiContext(playwright: Playwright, config: TestConfig): APIRequestContext {
        return playwright.request().newContext(
            APIRequest.NewContextOptions().setBaseURL(config.apiBaseUrl)
        )
    }
}

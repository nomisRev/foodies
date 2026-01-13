package com.foodies.e2e.utils

import com.foodies.e2e.config.TestConfig
import com.microsoft.playwright.APIRequest
import com.microsoft.playwright.APIRequestContext
import com.microsoft.playwright.Playwright

object ApiHelpers {
    /**
     * Creates an APIRequestContext for making direct backend API calls.
     */
    fun createApiContext(playwright: Playwright): APIRequestContext {
        return playwright.request().newContext(
            APIRequest.NewContextOptions().setBaseURL(TestConfig.baseUrl)
        )
    }
}

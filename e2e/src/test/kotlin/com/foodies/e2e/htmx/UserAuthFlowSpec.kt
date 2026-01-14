package com.foodies.e2e.htmx

import com.foodies.e2e.AppBrowserType
import com.foodies.e2e.config
import com.foodies.e2e.context
import com.foodies.e2e.e2eSuite
import com.foodies.e2e.page
import com.microsoft.playwright.*
import com.microsoft.playwright.options.AriaRole
import java.nio.file.Files
import kotlin.test.assertTrue

val authSetupSpec by e2eSuite(browserType = AppBrowserType.WEBKIT) {
    test("Keycloak Authentication - should authenticate user and save session state") {
        val p = page()

        p.navigate("/", Page.NavigateOptions().setTimeout(5000.0))
        p.getByText("Log in").click()

        assertTrue(p.url().contains("keycloak"), "Should redirect to Keycloak")

        p.getByLabel("Username").fill(config.testUsername)
        p.getByLabel("Password", Page.GetByLabelOptions().setExact(true)).fill(config.testPassword)
        p.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Sign In")).click()

        p.waitForURL("/")

        assertTrue(p.getByText("Log out").isVisible, "Should show log out button after login")

        Files.createDirectories(config.storageStatePath.parent)
        context().storageState(BrowserContext.StorageStateOptions().setPath(config.storageStatePath))
    }

    test("Logout Flow - should logout user and redirect to Keycloak") {
        val p = page()

        p.navigate("/", Page.NavigateOptions().setTimeout(5000.0))

        // Click logout
        // TODO: We assume the user is already logged in via AuthSetup
        val logoutButton = p.getByText("Log out")
        assertTrue(logoutButton.isVisible, "Should show log out button")
        logoutButton.click()

        assertTrue(
            p.url().contains("/logout") || p.url().contains("keycloak"),
            "Should redirect to logout URL. Current URL: ${p.url()}"
        )

        p.waitForURL("/")

        assertTrue(p.getByText("Log in").isVisible, "Should show log in button after logout")
    }
}

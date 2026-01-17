package io.ktor.foodies.keycloak

import com.foodies.e2e.AppBrowserType
import com.foodies.e2e.e2eSuite
import com.foodies.e2e.keycloak
import com.foodies.e2e.page
import com.foodies.e2e.rabbit
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole.BUTTON
import com.microsoft.playwright.options.LoadState.NETWORKIDLE
import com.microsoft.playwright.options.WaitForSelectorState
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.disable
import io.ktor.foodies.server.test.channel
import io.ktor.foodies.server.test.eventually
import io.ktor.foodies.user.event.UserEvent
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

val xkeyCloakSpec by e2eSuite(browserType = AppBrowserType.WEBKIT) {
    val queueName = "profile.registration"

    suspend fun login(username: String, password: String = "password") {
        page().context().clearCookies()
        val authServerUrl = keycloak().authServerUrl
        val loginUrl = "$authServerUrl/realms/foodies-keycloak/account"
        page().navigate(loginUrl)
        if (page().url().contains("/protocol/openid-connect/auth")) {
            page().locator("#username").fill(username)
            page().locator("#password").fill(password)
            page().getByRole(BUTTON, Page.GetByRoleOptions().setName("Sign In")).click()
        }
        page().waitForURL { it.contains("/account") }
    }

    test("should publish REGISTER event to RabbitMQ") {
        page().context().clearCookies()
        val authServerUrl = keycloak().authServerUrl
        assertNotNull(authServerUrl)

        val registrationUrl =
            "$authServerUrl/realms/foodies-keycloak/protocol/openid-connect/registrations?client_id=foodies&response_type=code"

        page().navigate(registrationUrl)
        page().waitForLoadState(NETWORKIDLE)

        val timestamp = System.currentTimeMillis()
        val username = "test_user_$timestamp"
        val email = "test_user_$timestamp@example.com"
        val firstName = "Test"
        val lastName = "User"

        page().locator("#username").fill(username)
        page().locator("#password").fill("password123")
        page().locator("#password-confirm").fill("password123")
        page().locator("#email").fill(email)
        page().locator("#firstName").fill(firstName)
        page().locator("#lastName").fill(lastName)

        page().getByRole(BUTTON, Page.GetByRoleOptions().setName("Register")).click()

        rabbit().connectionFactory().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            eventually(timeout = 20.seconds) {
                val response = channel.basicGet(queueName, true)
                assertNotNull(response, "Should receive REGISTER event in RabbitMQ")
                val event = Json.decodeFromString<UserEvent>(response.body.decodeToString())
                assert(event is UserEvent.Registration)
                val registration = event as UserEvent.Registration
                assertEquals(email, registration.email)
                assertEquals(firstName, registration.firstName)
                assertEquals(lastName, registration.lastName)
                assertNotNull(registration.subject)
            }
        }
    }

    test("should publish UPDATE_PROFILE event to RabbitMQ", testConfig = TestConfig.disable()) {
        login("food_lover")

        val authServerUrl = keycloak().authServerUrl
        val accountUrl = "$authServerUrl/realms/foodies-keycloak/account"

        val newFirstName = "UpdatedFirstName"
        val newLastName = "UpdatedLastName"

        // Navigate directly to personal info
        page().navigate("$accountUrl/#/personal-info")

        val firstNameField =
            page().locator("input[name=\"firstName\"]")
                .also { it.waitFor(Locator.WaitForOptions().setTimeout(5000.0).setState(WaitForSelectorState.VISIBLE)) }

        eventually(timeout = 15.seconds) {
            if (!firstNameField.isEnabled) firstNameField.click()
            assert(firstNameField.isEnabled) { "First name field should be enabled. Page content: ${page().content()}" }
        }

        firstNameField.fill(newFirstName)
        page().locator("input[name=\"lastName\"]").fill(newLastName)

        page().getByRole(BUTTON, Page.GetByRoleOptions().setName("Save")).click()

        // Check that the UPDATE_PROFILE event is received in RabbitMQ
        rabbit().connectionFactory().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            eventually(timeout = 20.seconds) {
                val response = channel.basicGet(queueName, true)
                assertNotNull(response, "Should receive UPDATE_PROFILE event in RabbitMQ")
                val event = Json.decodeFromString<UserEvent>(response.body.decodeToString())
                assert(event is UserEvent.UpdateProfile)
                val update = event as UserEvent.UpdateProfile
                assertEquals(newFirstName, update.firstName)
                assertEquals(newLastName, update.lastName)
                assertEquals("food_lover@gmail.com", update.email)
            }
        }
    }

    test("should publish DELETE_ACCOUNT event to RabbitMQ", testConfig = TestConfig.disable()) {
        val authServerUrl = keycloak().authServerUrl
        val timestamp = System.currentTimeMillis()
        val username = "delete_me_$timestamp"
        val email = "$username@example.com"

        // 1. Register a new user
        val registrationUrl =
            "$authServerUrl/realms/foodies-keycloak/protocol/openid-connect/registrations?client_id=foodies&response_type=code"
        page().navigate(registrationUrl)
        page().locator("#username").fill(username)
        page().locator("#password").fill("password123")
        page().locator("#password-confirm").fill("password123")
        page().locator("#email").fill(email)
        page().locator("#firstName").fill("Delete")
        page().locator("#lastName").fill("Me")
        page().getByRole(BUTTON, Page.GetByRoleOptions().setName("Register")).click()

        // Purge the registration event from the queue
        rabbit().connectionFactory().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            eventually(timeout = 15.seconds) {
                assertNotNull(channel.basicGet(queueName, true))
            }
        }

        // 2. Login as the new user
        login(username, "password123")

        // 3. Go to delete account page
        val accountUrl = "$authServerUrl/realms/foodies-keycloak/account"
        page().navigate("$accountUrl/#/account-security/delete-account")

        // Wait for delete button
        val deleteButton = page().locator("#delete-account-btn")
        eventually(timeout = 15.seconds) {
            assert(deleteButton.isVisible) { "Delete button should be visible. Page content: ${page().content()}" }
        }
        deleteButton.click()

        // Confirm deletion in dialog
        page().getByRole(BUTTON, Page.GetByRoleOptions().setName("Delete")).click()

        // Check that the DELETE event is received in RabbitMQ
        rabbit().connectionFactory().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            eventually(timeout = 20.seconds) {
                val response = channel.basicGet(queueName, true)
                assertNotNull(response, "Should receive DELETE event in RabbitMQ")
                val event = Json.decodeFromString<UserEvent>(response.body.decodeToString())
                assert(event is UserEvent.Delete)
                assertNotNull(event.subject)
            }
        }
    }
}

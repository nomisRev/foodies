package io.ktor.foodies.keycloak

import com.foodies.e2e.e2eSuite
import com.foodies.e2e.keycloak
import com.foodies.e2e.page
import com.foodies.e2e.rabbit
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import io.ktor.foodies.server.test.channel
import io.ktor.foodies.server.test.eventually
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val keyCloakSpec by e2eSuite(testConfig = TestConfig.testScope(true, timeout = 3.minutes)) {
    test("should publish REGISTER event to RabbitMQ") {
        val authServerUrl = keycloak().authServerUrl
        assertNotNull(authServerUrl)

        val registrationUrl = "$authServerUrl/realms/foodies-keycloak/protocol/openid-connect/registrations?client_id=foodies&response_type=code"

        page().navigate(registrationUrl)
        page().waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE)

        // Fill out registration form
        val timestamp = System.currentTimeMillis()
        val username = "test_user_$timestamp"
        val email = "test_user_$timestamp@example.com"

        page().locator("#username").fill(username)
        page().locator("#password").fill("password123")
        page().locator("#password-confirm").fill("password123")
        page().locator("#email").fill(email)
        page().locator("#firstName").fill("Test")
        page().locator("#lastName").fill("User")

        // Submit registration
        page().getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, com.microsoft.playwright.Page.GetByRoleOptions().setName("Register")).click()

        // Check that the REGISTER event is received in RabbitMQ
        rabbit().connectionFactory().channel { channel ->
            val queueName = "profile.registration"
            channel.queueDeclare(queueName, true, false, false, null)
            eventually(timeout = 5.seconds) {
                val response = channel.basicGet(queueName, true)
                assertNotNull(response, "Should receive REGISTER event in RabbitMQ")
            }
        }
    }
}

package io.ktor.foodies.keycloak

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import com.microsoft.playwright.options.LoadState
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

        val registrationUrl =
            "$authServerUrl/realms/foodies-keycloak/protocol/openid-connect/registrations?client_id=foodies&response_type=code&redirect_uri=http://localhost:8080/oauth/callback"

        page().navigate(registrationUrl)
        page().waitForLoadState(LoadState.NETWORKIDLE)

        val timestamp = System.currentTimeMillis()
        val username = "test_user_$timestamp"
        val email = "test_user_$timestamp@example.com"

        page().locator("#username").fill(username)
        page().locator("#password").fill("password123")
        page().locator("#password-confirm").fill("password123")
        page().locator("#email").fill(email)
        page().locator("#firstName").fill("Test")
        page().locator("#lastName").fill("User")

        page().getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Register")).click()

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

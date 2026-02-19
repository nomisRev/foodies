package io.ktor.foodies.keycloak

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import dasniko.testcontainers.keycloak.KeycloakContainer
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.TestFixture
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.framework.shared.TestDisplayName
import de.infix.testBalloon.framework.shared.TestElementName
import de.infix.testBalloon.framework.shared.TestRegistering
import io.ktor.foodies.server.test.RabbitContainer
import io.ktor.foodies.server.test.rabbitContainer
import org.testcontainers.Testcontainers
import org.testcontainers.utility.MountableFile
import java.nio.file.Paths

fun TestSuite.keycloak(rabbit: TestFixture<RabbitContainer>) = testFixture {
    val rabbit = rabbit()
    Testcontainers.exposeHostPorts(rabbit.amqpPort)
    val image = System.getProperty("keycloak.image") ?: "foodies-keycloak:0.0.5"
    KeycloakContainer(image).apply {
        withEnv("RABBITMQ_HOST", "host.testcontainers.internal")
        withEnv("RABBITMQ_PORT", rabbit.amqpPort.toString())
        withEnv("RABBITMQ_USERNAME", rabbit.adminUsername)
        withEnv("RABBITMQ_PASSWORD", rabbit.adminPassword)
        val realmFile = Paths.get("../k8s/base/keycloak/realm-common.json").toAbsolutePath().normalize()
        withCopyFileToContainer(MountableFile.forHostPath(realmFile), "/opt/keycloak/data/import/realm.json")
        start()
        val clients = keycloakAdminClient.realm("foodies-keycloak").clients()
        val existingClient = clients.findByClientId("foodies").firstOrNull()
            ?: error("Expected Keycloak client 'foodies' from realm import.")
        existingClient.redirectUris = listOf("http://localhost:8080/oauth/callback")
        existingClient.webOrigins = listOf("http://localhost")
        existingClient.attributes = mapOf("post.logout.redirect.uris" to "http://localhost/*")
        clients[existingClient.id].update(existingClient)
    }
}

data class E2EContext(
    val playwright: TestFixture<Playwright>,
    val browser: TestFixture<Browser>,
    val page: TestFixture<Page>,
    val rabbit: TestFixture<RabbitContainer>,
    val keycloak: TestFixture<KeycloakContainer>
)

context(ctx: E2EContext)
suspend fun page() = ctx.page.invoke()

context(ctx: E2EContext)
suspend fun keycloak() = ctx.keycloak.invoke()

context(ctx: E2EContext)
suspend fun rabbit() = ctx.rabbit.invoke()

enum class AppBrowserType { CHROMIUM, FIREFOX, WEBKIT }

@TestRegistering
fun e2eSuite(
    @TestElementName name: String = "",
    @TestDisplayName displayName: String = name,
    testConfig: TestConfig = TestConfig,
    browserType: AppBrowserType = AppBrowserType.WEBKIT,
    content: context(E2EContext) TestSuite.() -> Unit
): Lazy<TestSuite> = testSuite(name, displayName, testConfig) {
    val rabbit = rabbitContainer()
    val keycloak = keycloak(rabbit)
    val playwright = testFixture { Playwright.create() }
    val browser = testFixture {
        when (browserType) {
            AppBrowserType.CHROMIUM -> playwright().chromium()
            AppBrowserType.FIREFOX -> playwright().firefox()
            AppBrowserType.WEBKIT -> playwright().webkit()
        }.launch(BrowserType.LaunchOptions().setHeadless(true))
    }

    val page = testFixture { browser().newPage() }

    content(E2EContext(playwright, browser, page, rabbit, keycloak), this)
}

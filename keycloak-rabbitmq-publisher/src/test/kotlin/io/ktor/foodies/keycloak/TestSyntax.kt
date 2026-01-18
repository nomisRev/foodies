package com.foodies.e2e

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import dasniko.testcontainers.keycloak.KeycloakContainer
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.framework.shared.TestDisplayName
import de.infix.testBalloon.framework.shared.TestElementName
import de.infix.testBalloon.framework.shared.TestRegistering
import io.ktor.foodies.server.test.RabbitContainer
import io.ktor.foodies.server.test.rabbitContainer
import kotlinx.serialization.json.buildJsonObject
import org.apache.commons.compress.harmony.pack200.PackingUtils.config
import org.testcontainers.Testcontainers
import org.testcontainers.utility.MountableFile
import java.nio.file.Files
import java.nio.file.Paths

fun TestSuite.keycloak(rabbit: TestSuite.Fixture<RabbitContainer>) = testFixture {
    val rabbit = rabbit()
    Testcontainers.exposeHostPorts(rabbit.amqpPort)
    val image = System.getProperty("keycloak.image") ?: "foodies-keycloak:0.0.5"
    KeycloakContainer(image).apply {
        withEnv("RABBITMQ_HOST", "host.testcontainers.internal")
        withEnv("RABBITMQ_PORT", rabbit.amqpPort.toString())
        withEnv("RABBITMQ_USERNAME", rabbit.adminUsername)
        withEnv("RABBITMQ_PASSWORD", rabbit.adminPassword)
        val realmFile = Paths.get("../keycloak/realm.json").toAbsolutePath().normalize()
        withCopyFileToContainer(MountableFile.forHostPath(realmFile), "/opt/keycloak/data/import/realm.json")
        start()
    }
}

data class E2EContext(
    val playwright: TestSuite.Fixture<Playwright>,
    val browser: TestSuite.Fixture<Browser>,
    val page: TestSuite.Fixture<Page>,
    val rabbit: TestSuite.Fixture<RabbitContainer>,
    val keycloak: TestSuite.Fixture<KeycloakContainer>
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
    browserType: AppBrowserType = AppBrowserType.CHROMIUM,
    authenticated: Boolean = true,
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

package io.ktor.foodies.keycloak

import com.rabbitmq.client.ConnectionFactory
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.framework.shared.TestDisplayName
import de.infix.testBalloon.framework.shared.TestElementName
import de.infix.testBalloon.framework.shared.TestRegistering
import io.ktor.foodies.server.test.RabbitContainer
import io.ktor.foodies.server.test.rabbitContainer
import kotlinx.serialization.json.Json

val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

internal fun RabbitContainer.config(queueName: String) = RabbitConfig(
    host,
    amqpPort,
    adminUsername,
    adminPassword,
    queueName
)

class RabbitContext(
    val container: TestSuite.Fixture<RabbitContainer>,
    val factory: TestSuite.Fixture<ConnectionFactory>
)

context(ctx: RabbitContext)
val container: TestSuite.Fixture<RabbitContainer>
    get() = ctx.container

context(ctx: RabbitContext)
val factory: TestSuite.Fixture<ConnectionFactory>
    get() = ctx.factory

@TestRegistering
context(ctx: RabbitContext)
internal fun TestSuite.testListener(name: String, block: suspend (String, ProfileWebhookEventListener) -> Unit) =
    test(name) {
        val queueName = name.split(" ").joinToString(separator = ".").lowercase()
        val connection = ctx.factory().newConnection()
        val channel = connection.createChannel().apply {
            queueDeclare(queueName, true, false, false, null)
        }
        val listener = ProfileWebhookEventListener(ctx.container().config(queueName), connection, channel)
        try {
            block(queueName, listener)
        } finally {
            listener.close()
        }
    }

@TestRegistering
fun rabbitSuite(
    @TestElementName name: String = "",
    @TestDisplayName displayName: String = name,
    testConfig: TestConfig = TestConfig,
    content: context(RabbitContext) TestSuite.() -> Unit
): Lazy<TestSuite> = testSuite(name, displayName, testConfig) {
    val container: TestSuite.Fixture<RabbitContainer> = rabbitContainer()
    val factory: TestSuite.Fixture<ConnectionFactory> = testFixture { container().connectionFactory() }
    content(RabbitContext(container, factory), this)
}

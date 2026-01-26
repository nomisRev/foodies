package io.ktor.foodies.keycloak

import com.rabbitmq.client.ConnectionFactory
import de.infix.testBalloon.framework.core.TestFixture
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.shared.TestRegistering
import io.ktor.foodies.server.test.RabbitContainer
import io.ktor.foodies.server.test.rabbitContainer

internal fun RabbitContainer.config(queueName: String) = RabbitConfig(
    host,
    amqpPort,
    adminUsername,
    adminPassword,
    queueName
)

class RabbitContext(
    val container: TestFixture<RabbitContainer>,
    val factory: TestFixture<ConnectionFactory>
)

fun TestSuite.rabbitContext(): RabbitContext {
    val container = rabbitContainer()
    return RabbitContext(container, testFixture { container().connectionFactory() })
}

context(ctx: RabbitContext)
val factory: TestFixture<ConnectionFactory>
    get() = ctx.factory

@TestRegistering
context(ctx: RabbitContext)
internal fun TestSuite.testListener(name: String, block: suspend (String, ProfileWebhookEventListener) -> Unit) =
    test(name) {
        val queueName = name.split(" ").joinToString(separator = ".").lowercase()
        val connection = ctx.factory().newConnection()
        val listener = ProfileWebhookEventListener(ctx.container().config(queueName), lazy { connection })
        try {
            block(queueName, listener)
        } finally {
            listener.close()
        }
    }

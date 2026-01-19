package io.ktor.foodies.server.test

import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConnectionFactory
import de.infix.testBalloon.framework.core.TestSuite
import org.testcontainers.containers.RabbitMQContainer

class RabbitContainer internal constructor() : RabbitMQContainer("rabbitmq:4.2.2-alpine") {
    fun connectionFactory(): ConnectionFactory =
        ConnectionFactory().apply {
            host = this@RabbitContainer.host
            port = amqpPort
            username = adminUsername
            password = adminPassword
        }
}

fun TestSuite.rabbitContainer(): TestSuite.Fixture<RabbitContainer> =
    testFixture { RabbitContainer().apply { start() } } closeWith { stop() }

inline fun <A> ConnectionFactory.channel(block: (Channel) -> A): A =
    newConnection().use { connection ->
        connection.createChannel().use { channel -> block(channel) }
    }

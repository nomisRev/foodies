package io.ktor.foodies.server.test

import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConnectionFactory
import de.infix.testBalloon.framework.core.TestSuite
import org.testcontainers.containers.RabbitMQContainer

class RabbitContainer internal constructor() : RabbitMQContainer("rabbitmq:4.2.2-alpine")

fun TestSuite.rabbitContainer(): TestSuite.Fixture<RabbitContainer> =
    testFixture { RabbitContainer().apply { start() } } closeWith { stop() }

fun TestSuite.connectionFactory() =
    testFixture {
        val container = rabbitContainer()()
        ConnectionFactory().apply {
            host = container.host
            port = container.amqpPort
            username = container.adminUsername
            password = container.adminPassword
        }
    }

inline fun <A> ConnectionFactory.channel(block: (Channel) -> A): A =
    newConnection().use { connection ->
        connection.createChannel().use { channel ->
            block(channel)
        }
    }

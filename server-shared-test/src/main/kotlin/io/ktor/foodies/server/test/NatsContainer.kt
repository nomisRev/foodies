package io.ktor.foodies.server.test

import de.infix.testBalloon.framework.core.TestSuite
import io.nats.client.Nats
import io.nats.client.Options
import org.testcontainers.containers.GenericContainer

class NatsTestContainer internal constructor() : GenericContainer<NatsTestContainer>("nats:2.11-alpine") {
    init {
        withExposedPorts(4222)
        withCommand("-js")
    }

    val natsUrl: String get() = "nats://$host:${getMappedPort(4222)}"

    fun connection(): io.nats.client.Connection {
        val options = Options.builder()
            .server(natsUrl)
            .build()
        return Nats.connect(options)
    }
}

fun TestSuite.natsContainer(): TestSuite.Fixture<NatsTestContainer> =
    testFixture { NatsTestContainer().apply { start() } } closeWith { stop() }

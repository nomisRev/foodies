package io.ktor.foodies.server.consumers

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.server.customers.migratedPostgresDataSource
import io.ktor.foodies.server.profile.ExposedProfileRepository
import io.ktor.foodies.server.test.channel
import io.ktor.foodies.server.test.rabbitContainer
import io.ktor.foodies.user.event.UserEvent
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

val newUserConsumerSpec by testSuite {
    val dataSource = migratedPostgresDataSource()
    val rabbit = testFixture { rabbitContainer()().connectionFactory() }
    val repository = testFixture { ExposedProfileRepository(dataSource().database) }
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    test("creates profile when consuming new user event from RabbitMQ") {
        val queueName = "profile.registration.test"

        val payload = UserEvent.Registration(
            subject = "user-integration-test",
            email = "integration@example.com",
            firstName = "Integration",
            lastName = "Test",
        )
        val body = json.encodeToString(UserEvent.serializer(), payload)

        rabbit().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            channel.basicPublish("", queueName, null, body.toByteArray())
        }

        rabbit().channel { channel ->
            val messagesFlow = channel.messages<UserEvent>(queueName, json)
            val consumer = userEventConsumer(messagesFlow, repository())
            consumer.process().first()
        }

        val profile = repository().findBySubject("user-integration-test")
        assertNotNull(profile)
        assertEquals("user-integration-test", profile.subject)
        assertEquals("integration@example.com", profile.email)
        assertEquals("Integration", profile.firstName)
        assertEquals("Test", profile.lastName)
    }

    test("creates profile with null optional fields") {
        val queueName = "profile.registration.minimal"

        val payload = UserEvent.Registration(
            subject = "minimal-user",
            email = "integration@example.com",
            firstName = "Integration",
            lastName = "Test",
        )
        val body = json.encodeToString(UserEvent.serializer(), payload)

        rabbit().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            channel.basicPublish("", queueName, null, body.toByteArray())
        }

        rabbit().channel { channel ->
            val messagesFlow = channel.messages<UserEvent>(queueName, json)
            val consumer = userEventConsumer(messagesFlow, repository())
            consumer.process().first()
        }

        val profile = repository().findBySubject("minimal-user")
        assertNotNull(profile)
        assertEquals("minimal-user", profile.subject)
        assertEquals("Integration", profile.firstName)
        assertEquals("Test", profile.lastName)
        assertEquals("integration@example.com", profile.email)
    }
}

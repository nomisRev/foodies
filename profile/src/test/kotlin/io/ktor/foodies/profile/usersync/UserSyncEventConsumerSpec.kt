package io.ktor.foodies.profile.usersync

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.events.user.UserEvent
import io.ktor.foodies.profile.migratedPostgresDataSource
import io.ktor.foodies.profile.persistence.ExposedProfileRepository
import io.ktor.foodies.rabbitmq.RabbitMQSubscriber
import io.ktor.foodies.server.test.channel
import io.ktor.foodies.server.test.rabbitContainer
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

val userSyncEventConsumerSpec by testSuite {
    val dataSource = migratedPostgresDataSource()
    val rabbit = testFixture { rabbitContainer()().connectionFactory() }
    val repository = testFixture { ExposedProfileRepository(dataSource().database) }

    test("creates profile when consuming registration event") {
        val queueName = "usersync.registration.test"
        val payload = UserEvent.Registration(
            subject = "usersync-test-user",
            email = "usersync@example.com",
            firstName = "UserSync",
            lastName = "Test",
        )
        val body = Json.encodeToString(UserEvent.serializer(), payload)

        rabbit().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            channel.basicPublish("", queueName, null, body.toByteArray())
        }

        rabbit().newConnection().use { connection ->
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe(UserEvent.serializer(), queueName)
            userSyncEventConsumer(messagesFlow, repository()).first()
        }

        val profile = repository().findBySubject("usersync-test-user")
        assertNotNull(profile)
        assertEquals("usersync-test-user", profile.subject)
        assertEquals("usersync@example.com", profile.email)
        assertEquals("UserSync", profile.firstName)
        assertEquals("Test", profile.lastName)
    }

    test("updates profile when consuming update event") {
        val queueName = "usersync.update.test"
        repository().insertOrIgnore(
            subject = "usersync-update-user",
            email = "old@example.com",
            firstName = "Old",
            lastName = "Name",
        )

        val payload = UserEvent.UpdateProfile(
            subject = "usersync-update-user",
            email = "new@example.com",
            firstName = "New",
            lastName = "Name",
        )
        val body = Json.encodeToString(UserEvent.serializer(), payload)

        rabbit().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            channel.basicPublish("", queueName, null, body.toByteArray())
        }

        rabbit().newConnection().use { connection ->
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe(UserEvent.serializer(), queueName)
            userSyncEventConsumer(messagesFlow, repository()).first()
        }

        val profile = repository().findBySubject("usersync-update-user")
        assertNotNull(profile)
        assertEquals("new@example.com", profile.email)
        assertEquals("New", profile.firstName)
    }

    test("deletes profile when consuming delete event") {
        val queueName = "usersync.delete.test"
        repository().insertOrIgnore(
            subject = "usersync-delete-user",
            email = "delete@example.com",
            firstName = "Delete",
            lastName = "Me",
        )

        val payload = UserEvent.Delete(subject = "usersync-delete-user")
        val body = Json.encodeToString(UserEvent.serializer(), payload)

        rabbit().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            channel.basicPublish("", queueName, null, body.toByteArray())
        }

        rabbit().newConnection().use { connection ->
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe(UserEvent.serializer(), queueName)
            userSyncEventConsumer(messagesFlow, repository()).first()
        }

        val profile = repository().findBySubject("usersync-delete-user")
        assertNull(profile)
    }
}

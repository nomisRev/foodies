package io.ktor.foodies.server.consumers

import com.rabbitmq.client.ConnectionFactory
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.server.customers.migratedPostgresDataSource
import io.ktor.foodies.server.profile.ExposedProfileRepository
import io.ktor.foodies.server.profile.Profile
import io.ktor.foodies.server.profile.ProfileTable
import io.ktor.foodies.server.test.PostgreSQLContainer
import io.ktor.foodies.server.test.RabbitContainer
import io.ktor.foodies.server.test.channel
import io.ktor.foodies.server.test.connectionFactory
import io.ktor.foodies.server.test.postgresContainer
import io.ktor.foodies.server.test.rabbitContainer
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

val newUserConsumerSpec by testSuite {
    val dataSource = migratedPostgresDataSource()
    val rabbit = connectionFactory()
    val repository = testFixture { ExposedProfileRepository(dataSource().database) }
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    test("creates profile when consuming new user event from RabbitMQ") {
        val queueName = "profile.registration.test"

        val payload = NewUserEvent(
            subject = "user-integration-test",
            email = "integration@example.com",
            firstName = "Integration",
            lastName = "Test",
        )
        val body = json.encodeToString(NewUserEvent.serializer(), payload)

        rabbit().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            channel.basicPublish("", queueName, null, body.toByteArray())
        }

        rabbit().channel { channel ->
            val messagesFlow = channel.messages<NewUserEvent>(queueName, json)
            val consumer = newUserConsumer(messagesFlow, repository())
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

        val payload = NewUserEvent(
            subject = "minimal-user",
            email = "integration@example.com",
            firstName = "Integration",
            lastName = "Test",
        )
        val body = json.encodeToString(NewUserEvent.serializer(), payload)

        rabbit().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            channel.basicPublish("", queueName, null, body.toByteArray())
        }

        rabbit().channel { channel ->
            val messagesFlow = channel.messages<NewUserEvent>(queueName, json)
            val consumer = newUserConsumer(messagesFlow, repository())
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

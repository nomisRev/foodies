package io.ktor.foodies.keycloak

import io.ktor.foodies.server.test.channel
import io.ktor.foodies.user.event.UserEvent
import kotlinx.serialization.json.Json
import org.keycloak.events.Event
import org.keycloak.events.EventType
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

val userRegistrationEvent by rabbitSuite {
    testListener("publishes NewUserEvent to RabbitMQ when REGISTER event is received") { queueName, listener ->
        val event = Event().apply {
            type = EventType.REGISTER
            userId = "test-user-123"
            details = mapOf(
                "email" to "test@example.com",
                "first_name" to "John",
                "last_name" to "Doe"
            )
        }

        listener.onEvent(event)

        factory().channel { channel ->
            val response = channel.basicGet(queueName, true)
            assertNotNull(response, "Expected a message in the queue")
            assertEquals(
                UserEvent.Registration(
                    subject = "test-user-123",
                    email = "test@example.com",
                    firstName = "John",
                    lastName = "Doe"
                ),
                Json.decodeFromString<UserEvent>(response.body.decodeToString())
            )
        }
    }

    testListener("publishes NewUserEvent with null optional fields when details are missing") { queueName, listener ->
        val event = Event().apply {
            type = EventType.REGISTER
            userId = "minimal-user-456"
            details = emptyMap()
        }

        val ex = assertFailsWith<IllegalStateException> { listener.onEvent(event) }
        assertEquals(
            "Missing required fields for registration event: userId=minimal-user-456 email=null firstName=null, lastName=null",
            ex.message
        )
    }

    testListener("does not publish message when event is null") { queueName, listener ->
        listener.onEvent(null)

        factory().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            val response = channel.basicGet(queueName, true)
            assertNull(response, "Expected no message in the queue")
        }
    }

    testListener("does not publish message when event type is not REGISTER") { queueName, listener ->
        val event = Event().apply {
            type = EventType.LOGIN
            userId = "login-user-789"
            details = mapOf(
                "email" to "login@example.com",
                "first_name" to "Jane",
                "last_name" to "Smith"
            )
        }

        listener.onEvent(event)

        factory().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            val response = channel.basicGet(queueName, true)
            assertNull(response, "Expected no message in the queue for non-REGISTER event")
        }
    }

    testListener("handles event with null details map") { queueName, listener ->
        val event = Event().apply {
            type = EventType.REGISTER
            userId = "null-details-user"
            details = null
        }

        val ex = assertFailsWith<IllegalStateException> { listener.onEvent(event) }
        assertEquals(
            "Missing required fields for registration event: userId=null-details-user email=null firstName=null, lastName=null",
            ex.message
        )
    }

    testListener("handles event with null userId") { queueName, listener ->
        val event = Event().apply {
            type = EventType.REGISTER
            userId = null
            details = mapOf("email" to "nulluser@example.com")
        }

        val ex = assertFailsWith<IllegalStateException> { listener.onEvent(event) }
        assertEquals(
            "Missing required fields for registration event: userId=null email=nulluser@example.com firstName=null, lastName=null",
            ex.message
        )
    }

    testListener("onEvent for AdminEvent does nothing") { queueName, listener ->
        // AdminEvent handler should do nothing
        listener.onEvent(null, true)
        listener.onEvent(null, false)

        factory().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            val response = channel.basicGet(queueName, true)
            assertNull(response, "Expected no message in the queue for admin events")
        }
    }

    testListener("close is idempotent") { _, listener ->
        listener.close()
    }
}

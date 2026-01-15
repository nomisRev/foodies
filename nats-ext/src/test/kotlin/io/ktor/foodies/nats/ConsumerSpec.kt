package io.ktor.foodies.nats

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.server.test.natsContainer
import io.nats.client.api.StreamConfiguration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Serializable
data class TestPayload(val id: String, val value: Int)

val consumerSpec = testSuite {
    val nats = testFixture { natsContainer()().connection() }

    test("messages - successfully deserializes and emits message") {
        val streamName = "test-stream"
        val subject = "test.subject"
        val durable = "test-durable"
        val payload = TestPayload(id = "test-1", value = 42)
        val body = Json.encodeToString(TestPayload.serializer(), payload)

        val connection = nats()
        val jsm = connection.jetStreamManagement()
        jsm.addStream(StreamConfiguration.builder()
            .name(streamName)
            .subjects(subject)
            .build())

        val js = connection.jetStream()
        js.publish(subject, body.toByteArray())

        val messagesFlow = js.messages<TestPayload>(streamName, subject, durable)
        val message = messagesFlow.first()

        assertEquals("test-1", message.value.id)
        assertEquals(42, message.value.value)
        message.ack()
    }

    test("messages - emits multiple messages in order") {
        val streamName = "test-stream-multiple"
        val subject = "test.multiple"
        val durable = "test-durable-multiple"
        val payloads = listOf(
            TestPayload(id = "msg-1", value = 1),
            TestPayload(id = "msg-2", value = 2),
            TestPayload(id = "msg-3", value = 3)
        )

        val connection = nats()
        val jsm = connection.jetStreamManagement()
        jsm.addStream(StreamConfiguration.builder()
            .name(streamName)
            .subjects(subject)
            .build())

        val js = connection.jetStream()
        payloads.forEach { payload ->
            val body = Json.encodeToString(TestPayload.serializer(), payload)
            js.publish(subject, body.toByteArray())
        }

        val messagesFlow = js.messages<TestPayload>(streamName, subject, durable)
        val messages = messagesFlow.take(3).toList()

        assertEquals(3, messages.size)
        assertEquals("msg-1", messages[0].value.id)
        assertEquals("msg-2", messages[1].value.id)
        assertEquals("msg-3", messages[2].value.id)

        messages.forEach { it.ack() }
    }

    test("messages - closes flow with error on JSON deserialization failure") {
        val streamName = "test-stream-error"
        val subject = "test.error"
        val durable = "test-durable-error"
        val invalidJson = "{ invalid json }"

        val connection = nats()
        val jsm = connection.jetStreamManagement()
        jsm.addStream(StreamConfiguration.builder()
            .name(streamName)
            .subjects(subject)
            .build())

        val js = connection.jetStream()
        js.publish(subject, invalidJson.toByteArray())

        val messagesFlow = js.messages<TestPayload>(streamName, subject, durable)
        val error = runCatching { messagesFlow.first() }.exceptionOrNull()

        assertNotNull(error, "Expected an exception due to invalid JSON")
    }
}

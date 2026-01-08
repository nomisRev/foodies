package io.ktor.foodies.server.consumers

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.server.test.channel
import io.ktor.foodies.server.test.rabbitContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@Serializable
data class TestPayload(val id: String, val value: Int)

val consumerSpec by testSuite {
    val rabbit = testFixture { rabbitContainer()().connectionFactory() }
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    test("messages - successfully deserializes and emits message") {
        val queueName = "consumer.test.success"
        val payload = TestPayload(id = "test-1", value = 42)
        val body = json.encodeToString(TestPayload.serializer(), payload)

        rabbit().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            channel.basicPublish("", queueName, null, body.toByteArray())
        }

        rabbit().channel { channel ->
            val messagesFlow = channel.messages<TestPayload>(queueName, json)
            val message = messagesFlow.first()

            assertEquals("test-1", message.value.id)
            assertEquals(42, message.value.value)
            message.ack()
        }
    }

    test("messages - emits multiple messages in order") {
        val queueName = "consumer.test.multiple"
        val payloads = listOf(
            TestPayload(id = "msg-1", value = 1),
            TestPayload(id = "msg-2", value = 2),
            TestPayload(id = "msg-3", value = 3)
        )

        rabbit().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            payloads.forEach { payload ->
                val body = json.encodeToString(TestPayload.serializer(), payload)
                channel.basicPublish("", queueName, null, body.toByteArray())
            }

        }

        rabbit().channel { channel ->
            val messagesFlow = channel.messages<TestPayload>(queueName, json)
            val messages = messagesFlow.take(3).toList()

            assertEquals(3, messages.size)
            assertEquals("msg-1", messages[0].value.id)
            assertEquals("msg-2", messages[1].value.id)
            assertEquals("msg-3", messages[2].value.id)

            messages.forEach { it.ack() }
        }
    }

    test("messages - closes flow with error on JSON deserialization failure") {
        val queueName = "consumer.test.deserialize.error"
        val invalidJson = "{ invalid json }"

        rabbit().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            channel.basicPublish("", queueName, null, invalidJson.toByteArray())
        }

        rabbit().channel { channel ->
            val messagesFlow = channel.messages<TestPayload>(queueName, json)

            val error = runCatching { messagesFlow.first() }.exceptionOrNull()

            assertNotNull(error, "Expected an exception due to invalid JSON")
            assertTrue(
                error.message?.contains("Unexpected JSON token") == true ||
                        error.cause?.message?.contains("Unexpected JSON token") == true,
                "Expected JSON parsing error but got: ${error.message}"
            )
        }
    }

    test("Message::ack - acknowledges message to RabbitMQ") {
        val queueName = "consumer.test.ack"
        val payload = TestPayload(id = "ack-test", value = 100)
        val body = json.encodeToString(TestPayload.serializer(), payload)

        rabbit().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            channel.basicPublish("", queueName, null, body.toByteArray())
        }

        rabbit().channel { channel ->
            val messagesFlow = channel.messages<TestPayload>(queueName, json)
            val message = messagesFlow.first()
            message.ack()
        }

        rabbit().channel { channel ->
            val messagesFlow = channel.messages<TestPayload>(queueName, json)
            val result = withTimeoutOrNull(2.seconds) {
                messagesFlow.first()
            }
            assertNull(result, "Message should have been acknowledged and removed from queue")
        }
    }

    test("Message::nack - negative acknowledges message and removes it from queue") {
        val queueName = "consumer.test.nack"
        val payload = TestPayload(id = "nack-test", value = 200)
        val body = json.encodeToString(TestPayload.serializer(), payload)

        rabbit().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            channel.basicPublish("", queueName, null, body.toByteArray())
        }

        rabbit().channel { channel ->
            val messagesFlow = channel.messages<TestPayload>(queueName, json)
            val message = messagesFlow.first()

            assertEquals("nack-test", message.value.id)
            assertEquals(200, message.value.value)

            message.nack()
        }

        rabbit().channel { channel ->
            val messagesFlow = channel.messages<TestPayload>(queueName, json)
            val result = withTimeoutOrNull(2.seconds) {
                messagesFlow.first()
            }
            assertNull(result, "Message should have been nack'd and discarded from queue")
        }
    }

    test("Message::nack - message is discarded not requeued") {
        val queueName = "consumer.test.nack.discard"
        val payload = TestPayload(id = "discard-test", value = 300)
        val body = json.encodeToString(TestPayload.serializer(), payload)

        rabbit().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            channel.basicPublish("", queueName, null, body.toByteArray())
        }

        rabbit().channel { channel ->
            val messagesFlow = channel.messages<TestPayload>(queueName, json)
            val message = messagesFlow.first()
            message.nack() // requeue=false means message is discarded
        }

        rabbit().channel { channel ->
            val messagesFlow = channel.messages<TestPayload>(queueName, json)
            val result = withTimeoutOrNull(2.seconds) {
                messagesFlow.first()
            }
            assertNull(result, "Message should have been discarded after nack (requeue=false)")
        }
    }
}

package io.ktor.foodies.rabbitmq

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

    test("messages - successfully deserializes and emits message") {
        val queueName = "consumer.test.success"
        val payload = TestPayload(id = "test-1", value = 42)
        val body = Json.encodeToString(TestPayload.serializer(), payload)

        rabbit().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            channel.basicPublish("", queueName, null, body.toByteArray())
        }

        rabbit().newConnection().use { connection ->
            val message = RabbitMQSubscriber(connection, "exchange").subscribe<TestPayload>(queueName).first()
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
                val body = Json.encodeToString(TestPayload.serializer(), payload)
                channel.basicPublish("", queueName, null, body.toByteArray())
            }

        }

        rabbit().newConnection().use { connection ->
            val messages =  RabbitMQSubscriber(connection, "exchange").subscribe<TestPayload>(queueName).take(3).toList()

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

        rabbit().newConnection().use { connection ->
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe<TestPayload>(queueName)

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
        val body = Json.encodeToString(TestPayload.serializer(), payload)

        rabbit().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            channel.basicPublish("", queueName, null, body.toByteArray())
        }

        rabbit().newConnection().use { connection ->
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe<TestPayload>(queueName)
            val message = messagesFlow.first()
            message.ack()
        }

        rabbit().newConnection().use { connection ->
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe<TestPayload>(queueName)
            val result = withTimeoutOrNull(2.seconds) {
                messagesFlow.first()
            }
            assertNull(result, "Message should have been acknowledged and removed from queue")
        }
    }

    test("Message::nack - negative acknowledges message and removes it from queue") {
        val queueName = "consumer.test.nack"
        val payload = TestPayload(id = "nack-test", value = 200)
        val body = Json.encodeToString(TestPayload.serializer(), payload)

        rabbit().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            channel.basicPublish("", queueName, null, body.toByteArray())
        }

        rabbit().newConnection().use { connection ->
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe<TestPayload>(queueName)
            val message = messagesFlow.first()

            assertEquals("nack-test", message.value.id)
            assertEquals(200, message.value.value)

            message.nack()
        }

        rabbit().newConnection().use { connection ->
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe<TestPayload>(queueName)
            val result = withTimeoutOrNull(2.seconds) {
                messagesFlow.first()
            }
            assertNull(result, "Message should have been nack'd and discarded from queue")
        }
    }

    test("Message::nack - message is discarded not requeued") {
        val queueName = "consumer.test.nack.discard"
        val payload = TestPayload(id = "discard-test", value = 300)
        val body = Json.encodeToString(TestPayload.serializer(), payload)

        rabbit().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            channel.basicPublish("", queueName, null, body.toByteArray())
        }

        rabbit().newConnection().use { connection ->
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe<TestPayload>(queueName)
            val message = messagesFlow.first()
            message.nack() // requeue=false means message is discarded
        }

        rabbit().newConnection().use { connection ->
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe<TestPayload>(queueName)
            val result = withTimeoutOrNull(2.seconds) {
                messagesFlow.first()
            }
            assertNull(result, "Message should have been discarded after nack (requeue=false)")
        }
    }

    test("consumeMessage - acknowledges message on success") {
        val queueName = "consumeMessage.test.success"
        val payload = TestPayload(id = "consume-success", value = 1)
        val body = Json.encodeToString(TestPayload.serializer(), payload)

        rabbit().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            channel.basicPublish("", queueName, null, body.toByteArray())
        }

        rabbit().newConnection().use { connection ->
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe<TestPayload>(queueName)
            messagesFlow.parConsumeMessage {
                assertEquals("consume-success", it.id)
            }.first()
        }

        rabbit().newConnection().use { connection ->
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe<TestPayload>(queueName)
            val result = withTimeoutOrNull(2.seconds) {
                messagesFlow.first()
            }
            assertNull(result, "Message should have been acknowledged by consumeMessage")
        }
    }

    test("consumeMessage - nacks message on error") {
        val queueName = "consumeMessage.test.error"
        val payload = TestPayload(id = "consume-error", value = 1)
        val body = Json.encodeToString(TestPayload.serializer(), payload)

        rabbit().channel { channel ->
            channel.queueDeclare(queueName, true, false, false, null)
            channel.basicPublish("", queueName, null, body.toByteArray())
        }

        rabbit().newConnection().use { connection ->
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe<TestPayload>(queueName)
            runCatching {
                messagesFlow.parConsumeMessage {
                    throw RuntimeException("Processing failed")
                }.first()
            }
        }

        rabbit().newConnection().use { connection ->
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe<TestPayload>(queueName)
            val result = withTimeoutOrNull(2.seconds) {
                messagesFlow.first()
            }
            assertNull(result, "Message should have been nack'd by consumeMessage")
        }
    }
}

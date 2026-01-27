package io.ktor.foodies.rabbitmq

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.foodies.server.test.channel
import io.ktor.foodies.server.test.rabbitContainer
import kotlinx.coroutines.delay
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
            val message = RabbitMQSubscriber(connection, "exchange").subscribe(TestPayload.serializer(), queueName).first()
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
            val messages =  RabbitMQSubscriber(connection, "exchange").subscribe(TestPayload.serializer(), queueName).take(3).toList()

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
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe(TestPayload.serializer(), queueName)

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
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe(TestPayload.serializer(), queueName)
            val message = messagesFlow.first()
            message.ack()
        }

        rabbit().newConnection().use { connection ->
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe(TestPayload.serializer(), queueName)
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
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe(TestPayload.serializer(), queueName)
            val message = messagesFlow.first()

            assertEquals("nack-test", message.value.id)
            assertEquals(200, message.value.value)

            message.nack()
        }

        rabbit().newConnection().use { connection ->
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe(TestPayload.serializer(), queueName)
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
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe(TestPayload.serializer(), queueName)
            val message = messagesFlow.first()
            message.nack() // requeue=false means message is discarded
        }

        rabbit().newConnection().use { connection ->
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe(TestPayload.serializer(), queueName)
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
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe(TestPayload.serializer(), queueName)
            messagesFlow.parConsumeMessage {
                assertEquals("consume-success", it.id)
            }.first()
        }

        rabbit().newConnection().use { connection ->
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe(TestPayload.serializer(), queueName)
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
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe(TestPayload.serializer(), queueName)
            runCatching {
                messagesFlow.parConsumeMessage {
                    throw RuntimeException("Processing failed")
                }.first()
            }
        }

        rabbit().newConnection().use { connection ->
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe(TestPayload.serializer(), queueName)
            val result = withTimeoutOrNull(2.seconds) {
                messagesFlow.first()
            }
            assertNull(result, "Message should have been nack'd by consumeMessage")
        }
    }

    test("Message::deliveryAttempts - tracks x-death count from dead-letter queue") {
        val dlqName = "consumer.test.dlq"
        val dlxName = "consumer.test.dlx"
        val mainQueueName = "consumer.test.main.dlx"
        val payload = TestPayload(id = "retry-test", value = 999)
        val body = Json.encodeToString(TestPayload.serializer(), payload)

        rabbit().channel { channel ->
            channel.exchangeDeclare(dlxName, "direct", true)
            channel.queueDeclare(dlqName, true, false, false, null)
            channel.queueBind(dlqName, dlxName, mainQueueName)

            val args = mapOf("x-dead-letter-exchange" to dlxName)
            channel.queueDeclare(mainQueueName, true, false, false, args)
            channel.basicPublish("", mainQueueName, null, body.toByteArray())
        }

        rabbit().newConnection().use { connection ->
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe(
                TestPayload.serializer(),
                mainQueueName,
                RetryPolicy.None
            ) { }
            val message = messagesFlow.first()
            assertEquals(0, message.deliveryAttempts)
            message.nack()
        }

        rabbit().newConnection().use { connection ->
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe(
                TestPayload.serializer(),
                dlqName
            ) { }
            val dlqMessage = messagesFlow.first()
            assertEquals("retry-test", dlqMessage.value.id)
            assertTrue(dlqMessage.deliveryAttempts > 0, "Expected deliveryAttempts > 0, got ${dlqMessage.deliveryAttempts}")
            dlqMessage.ack()
        }
    }

    test("QueueOptions - x-message-ttl expires messages after duration") {
        val queueName = "consumer.test.ttl"
        val payload = TestPayload(id = "ttl-test", value = 777)
        val body = Json.encodeToString(TestPayload.serializer(), payload)

        rabbit().channel { channel ->
            val args = mapOf("x-message-ttl" to 1000L)
            channel.queueDeclare(queueName, true, false, false, args)
            channel.basicPublish("", queueName, null, body.toByteArray())
        }

        kotlinx.coroutines.delay(1500)

        rabbit().newConnection().use { connection ->
            val messagesFlow = RabbitMQSubscriber(connection, "exchange").subscribe(TestPayload.serializer(), queueName) { }
            val result = withTimeoutOrNull(2.seconds) {
                messagesFlow.first()
            }
            assertNull(result, "Message should have expired after TTL")
        }
    }
}

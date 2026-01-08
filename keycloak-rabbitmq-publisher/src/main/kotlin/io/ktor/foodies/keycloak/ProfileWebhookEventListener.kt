package io.ktor.foodies.keycloak

import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import kotlinx.serialization.json.Json
import org.jboss.logging.Logger
import org.keycloak.events.Event
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.EventType
import org.keycloak.events.admin.AdminEvent

internal class ProfileWebhookEventListener(private val rabbitConfig: RabbitConfig) : EventListenerProvider {

    private val logger = Logger.getLogger(ProfileWebhookEventListener::class.java)
    private val json = Json { encodeDefaults = true }

    private val connection: Connection
    private val channel: Channel

    init {
        val connectionFactory = ConnectionFactory().apply {
            host = rabbitConfig.host
            port = rabbitConfig.port
            username = rabbitConfig.username
            password = rabbitConfig.password
        }
        connection = connectionFactory.newConnection("profile-webhook")
        channel = connection.createChannel().apply {
            queueDeclare(rabbitConfig.queue, true, false, false, null)
        }
    }

    override fun onEvent(event: Event?) {
        if (event == null || event.type != EventType.REGISTER) return

        val details = event.details.orEmpty()

        val payload = NewUserEvent(
            subject = event.userId.orEmpty(),
            email = details["email"],
            firstName = details["first_name"],
            lastName = details["last_name"],
        )

        val message = json.encodeToString(payload)

        runCatching {
            channel.basicPublish("", rabbitConfig.queue, null, message.toByteArray())
        }.onFailure {
            logger.error(
                "Failed to forward registration event to profile queue ${rabbitConfig.queue} for userId=${payload.subject}",
                it
            )
        }
    }

    override fun onEvent(event: AdminEvent?, includeRepresentation: Boolean) = Unit

    override fun close() {
        runCatching { channel.close() }.onFailure { logger.warn("Failed to close RabbitMQ channel", it) }
        runCatching { connection.close() }.onFailure { logger.warn("Failed to close RabbitMQ connection", it) }
    }
}

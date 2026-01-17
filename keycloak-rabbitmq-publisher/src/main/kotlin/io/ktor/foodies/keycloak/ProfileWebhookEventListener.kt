package io.ktor.foodies.keycloak

import com.rabbitmq.client.Connection
import io.ktor.foodies.user.event.UserEvent
import kotlinx.serialization.json.Json
import org.jboss.logging.Logger
import org.keycloak.events.Event
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.admin.AdminEvent

internal class ProfileWebhookEventListener(
    private val rabbitConfig: RabbitConfig,
    private val connection: Lazy<Connection>,
) : EventListenerProvider {
    private val logger = Logger.getLogger(ProfileWebhookEventListener::class.java)
    private val channel by lazy {
        connection.value.createChannel().apply {
            queueDeclare(rabbitConfig.queue, true, false, false, null)
        }
    }

    override fun onEvent(event: AdminEvent?, includeRepresentation: Boolean) = Unit

    override fun onEvent(event: Event?) {
        logger.debugf("Received event: %s", event?.type)
        val userEvent = event?.toUserEvent() ?: return
        logger.debugf("Publishing user event: %s for subject %s", userEvent::class.simpleName, userEvent.subject)
        val message = Json.encodeToString(UserEvent.serializer(), userEvent)

        runCatching { channel.basicPublish("", rabbitConfig.queue, null, message.toByteArray()) }.onFailure {
            logger.error(
                "Failed to forward registration event to profile queue ${rabbitConfig.queue} for userId=${userEvent.subject}",
                it
            )
        }
    }

    override fun close() {
        runCatching { if (channel.isOpen) channel.close() }.onFailure {
            logger.warn(
                "Failed to close RabbitMQ channel",
                it
            )
        }
    }
}

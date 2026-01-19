package io.ktor.foodies.keycloak

import com.rabbitmq.client.Connection
import io.ktor.foodies.events.user.UserEvent
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
        val event = event?.toUserEvent() ?: return
        val message = Json.encodeToString(UserEvent.serializer(), event)

        runCatching { channel.basicPublish("", rabbitConfig.queue, null, message.toByteArray()) }
            .onFailure {
                logger.error(
                    "Failed to forward registration event to profile queue ${rabbitConfig.queue} for userId=${event.subject}",
                    it,
                )
            }
    }

    override fun close() {
        runCatching { if (channel.isOpen) channel.close() }
            .onFailure { logger.warn("Failed to close RabbitMQ channel", it) }
    }
}

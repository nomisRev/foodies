package io.ktor.foodies.keycloak

import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import io.ktor.foodies.user.event.UserEvent
import kotlinx.serialization.json.Json
import org.jboss.logging.Logger
import org.keycloak.events.Event
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.admin.AdminEvent

internal class ProfileWebhookEventListener(
    private val rabbitConfig: RabbitConfig,
    private val connection: Connection,
    private val channel: Channel,
) : EventListenerProvider {
    private val logger = Logger.getLogger(ProfileWebhookEventListener::class.java)
    private val json = Json { encodeDefaults = true }

    override fun onEvent(event: AdminEvent?, includeRepresentation: Boolean) = Unit

    override fun onEvent(event: Event?) {
        val event = event?.toUserEvent() ?: return
        val message = json.encodeToString(UserEvent.serializer(), event)

        runCatching { channel.basicPublish("", rabbitConfig.queue, null, message.toByteArray()) }.onFailure {
            logger.error(
                "Failed to forward registration event to profile queue ${rabbitConfig.queue} for userId=${event.subject}",
                it
            )
        }
    }

    override fun close() {
        runCatching { channel.close() }.onFailure { logger.warn("Failed to close RabbitMQ channel", it) }
        runCatching { connection.close() }.onFailure { logger.warn("Failed to close RabbitMQ connection", it) }
    }
}

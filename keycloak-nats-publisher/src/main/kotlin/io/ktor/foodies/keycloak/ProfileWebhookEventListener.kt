package io.ktor.foodies.keycloak

import io.ktor.foodies.user.event.UserEvent
import io.nats.client.Connection
import io.nats.client.JetStream
import kotlinx.serialization.json.Json
import org.jboss.logging.Logger
import org.keycloak.events.Event
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.admin.AdminEvent

internal class ProfileWebhookEventListener(
    private val natsConfig: NatsConfig,
    private val connection: Lazy<Connection>,
) : EventListenerProvider {
    private val logger = Logger.getLogger(ProfileWebhookEventListener::class.java)
    private val js: JetStream by lazy {
        connection.value.jetStream()
    }

    override fun onEvent(event: AdminEvent?, includeRepresentation: Boolean) = Unit

    override fun onEvent(event: Event?) {
        val userEvent = event?.toUserEvent() ?: return
        val message = Json.encodeToString(UserEvent.serializer(), userEvent)

        runCatching {
            js.publish(natsConfig.subject, message.toByteArray())
        }.onFailure {
            logger.error(
                "Failed to forward registration event to NATS subject ${natsConfig.subject} for userId=${userEvent.subject}",
                it
            )
        }
    }

    override fun close() {
        // Connection is managed by the factory/Keycloak lifecycle if shared,
        // but here it's a Lazy connection. We don't close it here as it might be shared.
    }
}

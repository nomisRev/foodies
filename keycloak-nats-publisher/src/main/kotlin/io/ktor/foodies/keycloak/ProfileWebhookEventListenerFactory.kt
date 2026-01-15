package io.ktor.foodies.keycloak

import io.nats.client.Nats
import io.nats.client.Options
import org.jboss.logging.Logger
import org.keycloak.Config
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.EventListenerProviderFactory
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory

private const val DEFAULT_STREAM_NAME = "profile"
private const val DEFAULT_SUBJECT_NAME = "profile.events"

class ProfileWebhookEventListenerFactory : EventListenerProviderFactory {
    private val logger = Logger.getLogger(ProfileWebhookEventListenerFactory::class.java)

    private val natsConfig: NatsConfig = NatsConfig(
        server = System.getenv("NATS_SERVER") ?: "nats://localhost:4222",
        stream = System.getenv("NATS_STREAM") ?: DEFAULT_STREAM_NAME,
        subject = System.getenv("NATS_SUBJECT") ?: DEFAULT_SUBJECT_NAME,
    )

    private val connection = lazy {
        val options = Options.builder()
            .server(natsConfig.server)
            .connectionName("profile-webhook")
            .build()
        val conn = Nats.connect(options)

        runCatching {
            val jsm = conn.jetStreamManagement()
            val streamExists = try {
                jsm.getStreamInfo(natsConfig.stream)
                true
            } catch (e: Exception) {
                false
            }

            if (!streamExists) {
                jsm.addStream(
                    io.nats.client.api.StreamConfiguration.builder()
                        .name(natsConfig.stream)
                        .subjects(natsConfig.subject)
                        .build()
                )
                logger.info("Created NATS stream: ${natsConfig.stream} with subject: ${natsConfig.subject}")
            }
        }.onFailure {
            logger.warn("Failed to ensure NATS stream ${natsConfig.stream} exists", it)
        }

        conn
    }

    override fun init(config: Config.Scope) {}
    override fun postInit(factory: KeycloakSessionFactory) = Unit
    override fun getId(): String = "profile-webhook-nats"

    override fun create(session: KeycloakSession): EventListenerProvider =
        ProfileWebhookEventListener(natsConfig, connection)

    override fun close() {
        runCatching {
            if (connection.isInitialized()) connection.value.close()
        }.onFailure { logger.warn("Failed to close NATS connection", it) }
    }
}
